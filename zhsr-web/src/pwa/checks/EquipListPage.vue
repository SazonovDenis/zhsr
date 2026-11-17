<template>
  <PwaPage
    white
    :back="goBack"
    :title="detail ? textOf(detail.equipListTemplate) : t('Опись')"
    :subtitle="detail ? formatDateTime(detail.completedDt) : ''"
  >
    <LoadSpinner v-if="busy.active.value && !detail" />
    <ErrorBox v-if="error" :error="error" :retry="load" class="view__error q-py-sm q-px-md" />

    <template v-if="detail">
      <div class="view__attrs">
        <span>{{ t('Вагон') }}</span>
        <span>{{ wagonShortText(detail.wagon) }}</span>
        <span>{{ t('Рейс') }}</span>
        <span>{{ detail.trip ? tripRefDatedText(detail.trip) : '—' }}</span>
        <span>{{ t('Составил') }}</span>
        <span><UserName :user="detail.createdByUsr" /></span>
        <!-- подпись — по центру строки с кнопкой фильтра -->
        <span class="self-center">{{ t('Нарушений') }}</span>
        <!-- «Показать только нарушения» — сразу за числом: в списке только позиции с нарушением; нарушений нет — кнопки нет -->
        <span class="view__violations">
          <span v-if="detail.violationCount > 0" class="view__bad-text">{{ detail.violationCount }}</span>
          <template v-else>{{ t('нет') }}</template>
          <CbButton v-if="detail.violationCount > 0" v-model="filter" :values="filterChoices" />
        </span>
        <span>{{ t('Заявка по недостаче') }}</span>
        <span>
          <router-link
            v-if="detail.repairRequest"
            :to="{ name: 'pwa-request', params: { id: String(detail.repairRequest.id) } }"
            class="view__link"
          >
            {{ detail.repairRequest.number }}
          </router-link>
          <template v-else>—</template>
        </span>
      </div>

      <div class="view__items">
        <div class="view__line view__head">
          <span>{{ t('Позиция') }}</span>
          <span>{{ t('Требуется') }}</span>
          <span>{{ t('Факт') }}</span>
        </div>
        <template v-for="{ item, depth } in rows" :key="item.id">
          <div v-if="item.unit === null" class="view__section q-pt-sm" :style="indent(depth)">
            {{ textOf(item) }}
          </div>
          <div v-else-if="valueOf(item.id)" class="view__pos q-py-sm" :style="indent(depth)">
            <div class="view__line view__line--plain">
              <!-- нарушение — красное название, без нарушения по нормативу — зелёное -->
              <span :class="toneClass(valueOf(item.id)!)">{{ textOf(item) }}, {{ item.unit }}</span>
              <span>{{ numText(valueOf(item.id)!.quantityRequired) || '—' }}</span>
              <span>{{ numText(valueOf(item.id)!.quantityActual) }}</span>
            </div>
            <!-- текст — v-text: переносы строк примечания видны (pre-wrap), а отступы разметки — нет -->
            <div v-if="valueOf(item.id)!.comment" class="view__comment q-mt-xs" v-text="valueOf(item.id)!.comment" />
            <PhotoStrip
              v-if="valueOf(item.id)!.photos.length > 0"
              :photos="photosOf(valueOf(item.id)!)"
              small
              class="q-mt-xs"
            />
          </div>
        </template>
      </div>
    </template>

    <!-- исправить — автор в течение 10 минут после сохранения; кнопка исчезает сама, когда срок вышел -->
    <template v-if="detail && listEditable(detail, now)" #bar>
      <BottomBar :actions="editActions" @action="edit" />
    </template>
  </PwaPage>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import UserName from '@/shared/components/UserName.vue'
import type { EquipListDetail, EquipListValue } from '@/api/equip'
import { equipPhotoUrl, fetchEquipList } from '@/api/equip'
import type { RequestPhoto } from '@/api/requests'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { numText, textOf } from '@/shared/checks/texts'
import { treeRows, treeRowsWhere } from '@/shared/checks/tree'
import type { ChoiceId, ChoiceItem } from '@/shared/components/choice/choice'
import CbButton from '@/shared/components/choice/CbButton.vue'
import { listEditable } from '@/shared/checks/rights'
import { valueTone } from '@/shared/checks/valueView'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { wagonShortText } from '@/shared/requests/requestView'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import PhotoStrip from '@/shared/requests/PhotoStrip.vue'
import { useNow } from '@/shared/trips/tripTimes'
import { loadCurrentUser } from '@/shared/user'
import type { BottomBarAction } from '../components/BottomBar.vue'
import BottomBar from '../components/BottomBar.vue'
import PwaPage from '../components/PwaPage.vue'

/**
 * Просмотр описи экипировки на телефоне (Ф-ЭКП-03): вагон, рейс, кто составил, позиции по разделам — требуемое и
 * фактическое количество, недостача выделена; заявка по недостаче — ссылкой на карточку заявки. Только при связи.
 */
const props = defineProps<{ id: string }>()

const router = useRouter()
const busy = provideBusy()
const detail = shallowRef<EquipListDetail | null>(null)
const error = ref<ShownError | null>(null)

const VIOLATIONS = 'violations'
const filterChoices: ChoiceItem[] = [{ id: VIOLATIONS, text: 'Показать только нарушения' }]

// фильтр списка: [VIOLATIONS] — только позиции с нарушением; сбрасывается при смене описи
const filter = ref<ChoiceId[]>([])
watch(
  () => props.id,
  () => (filter.value = []),
)

const rows = computed(() => {
  const all = detail.value ? treeRows(detail.value.items) : []
  if (!filter.value.includes(VIOLATIONS)) return all
  return treeRowsWhere(all, (item) => item.unit !== null && valueOf(item.id)?.violation === true)
})
const values = computed(() => new Map((detail.value?.values ?? []).map((v) => [v.equipListTemplateItem, v])))

function toneClass(v: EquipListValue): string {
  const tone = valueTone(v.violation, v.equipListItemNorm)
  return tone ? `view__name--${tone}` : ''
}

function valueOf(item: number): EquipListValue | undefined {
  return values.value.get(item)
}

// вложенность разделов — небольшим отступом: на телефоне каждый отступ отнимает ширину у текста
const indent = (depth: number) => ({ paddingLeft: `${depth * 0.5}rem` })

async function load() {
  error.value = null
  try {
    const [d] = await busy.run(
      Promise.all([fetchEquipList(Number(props.id)), dictManager.prepareDict('WagonType'), loadCurrentUser()]),
    )
    detail.value = d
  } catch (e) {
    error.value = shownError(e)
  }
}
void load()

function photosOf(v: EquipListValue): RequestPhoto[] {
  return v.photos.map((p) => ({ id: String(p), url: equipPhotoUrl(p) }))
}

const now = useNow()
const editActions: BottomBarAction[] = [{ key: 'edit', title: 'Внести изменения', kind: 'primary' }]

/** Исправить опись: заполнение с сохранёнными значениями; после сохранения — снова просмотр. */
function edit() {
  void router.replace({ name: 'pwa-equip-new', query: { edit: props.id } })
}

/** Мастер начала и заполнение: после сохранения «назад» в них не возвращает. */
const WIZARD_ROUTES = ['pwa-check-start', 'pwa-check-new', 'pwa-equip-new']

// назад — туда, откуда пришли; открыли напрямую или пришли из мастера и заполнения — в список проверок и описей
function goBack() {
  const back = window.history.state?.back as string | null | undefined
  if (!back) void router.push({ name: 'pwa-checks' })
  else if (WIZARD_ROUTES.includes(String(router.resolve(back).name))) void router.replace({ name: 'pwa-checks' })
  else router.back()
}
</script>

<style scoped lang="scss">
// число нарушений и кнопка фильтра — в одной строке
.view__violations {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.view__attrs {
  display: grid;
  grid-template-columns: 7.5rem minmax(0, 1fr);
  gap: 0.5rem 1rem;
  font-size: 0.8rem;

  > :nth-child(odd) {
    color: $zs-text-muted;
  }
}

.view__items {
  display: flex;
  flex-direction: column;
  font-size: 0.9rem;
}

.view__section {
  font-weight: 600;
}

.view__head {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.view__line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 4.5rem 3.5rem;
  gap: 0.5rem;
  border-bottom: 0.0625rem solid $zs-border-row;

  > :not(:first-child) {
    text-align: right;
  }

  // строка позиции: линия снизу — у всей позиции с примечанием и фото
  &--plain {
    border-bottom: 0;
  }
}

.view__pos {
  border-bottom: 0.0625rem solid $zs-border-row;
}

.view__comment {
  white-space: pre-wrap;
}

.view__name--bad {
  color: $zs-error-fg;
}

.view__name--ok {
  color: $zs-ok-fg;
}

.view__link {
  color: $primary;
}

.view__bad-text {
  color: $zs-error-fg;
  font-weight: 600;
}

.view__error {
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}
</style>
