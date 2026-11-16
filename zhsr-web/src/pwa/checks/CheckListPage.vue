<template>
  <PwaPage
    white
    :back="goBack"
    :title="detail ? textOf(detail.checkListTemplate) : t('Чек-лист')"
    :subtitle="detail ? formatDateTime(detail.completedDt) : ''"
  >
    <LoadSpinner v-if="busy.active.value && !detail" />
    <ErrorBox v-if="error" :error="error" :retry="load" class="view__error q-py-sm q-px-md" />

    <template v-if="detail">
      <div class="view__attrs">
        <span>{{ t('Объект проверки') }}</span>
        <span>{{ subject || '—' }}</span>
        <span>{{ t('Рейс') }}</span>
        <span>{{ detail.trip ? tripRefDatedText(detail.trip) : '—' }}</span>
        <span>{{ t('Проверяющий') }}</span>
        <span>{{ userName(detail.createdByUsr) }}</span>
        <!-- подпись — по центру строки с кнопкой фильтра -->
        <span class="self-center">{{ t('Нарушений') }}</span>
        <!-- «Показать только нарушения» — сразу за числом: в списке только нарушенные пункты; нарушений нет — кнопки нет -->
        <span class="view__violations">
          <span :class="{ 'view__bad-text': detail.violationCount > 0 }">{{ detail.violationCount }}</span>
          <CbButton v-if="detail.violationCount > 0" v-model="filter" :values="filterChoices" />
        </span>
      </div>

      <div class="view__items">
        <template v-for="{ item, depth } in rows" :key="item.id">
          <div v-if="item.valueKind === null" class="view__section q-pt-sm" :style="indent(depth)">
            {{ textOf(item) }}
          </div>
          <div v-else-if="valueOf(item.id)" class="view__item q-py-sm q-px-xs" :style="indent(depth)">
            <!-- нарушение — красное название, без нарушения по нормативу — зелёное -->
            <div class="view__line">
              <span :class="toneClass(valueOf(item.id)!)">{{ textOf(item) }}</span>
              <!-- ответ — в прямоугольной плашке: в порядке — зелёной, нарушение — красной, без норматива и без
                   ответа («—») — серой -->
              <span class="view__value">
                <StatusBadge :tone="answerKind(valueOf(item.id)!)">{{
                  answered(valueOf(item.id)!) ? valueText(valueOf(item.id)!) : '—'
                }}</StatusBadge>
              </span>
            </div>
            <div v-if="valueOf(item.id)!.comment" class="q-mt-xs">{{ valueOf(item.id)!.comment }}</div>
            <!-- заявка — с таким же отступом от описания, как описание от строки пункта -->
            <div v-if="valueOf(item.id)!.repairRequest" class="q-mt-xs">
              <router-link
                :to="{ name: 'pwa-request', params: { id: String(valueOf(item.id)!.repairRequest!.id) } }"
                class="view__link"
              >
                {{ t('Заявка {number}', { number: valueOf(item.id)!.repairRequest!.number }) }}
              </router-link>
            </div>
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

import type { CheckListDetail, CheckListValue } from '@/api/checks'
import { checkPhotoUrl, fetchCheckList } from '@/api/checks'
import type { RequestPhoto } from '@/api/requests'
import { t } from '@/boot/i18n'
import StatusBadge from '@/shared/components/StatusBadge.vue'
import { provideBusy } from '@/shared/busy'
import { numText, textOf } from '@/shared/checks/texts'
import { treeRows, treeRowsWhere } from '@/shared/checks/tree'
import type { ChoiceId, ChoiceItem } from '@/shared/components/choice/choice'
import CbButton from '@/shared/components/choice/CbButton.vue'
import { listEditable } from '@/shared/checks/rights'
import { answered, valueTone } from '@/shared/checks/valueView'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import PhotoStrip from '@/shared/requests/PhotoStrip.vue'
import { wagonShortText } from '@/shared/requests/requestView'
import { tripRefDatedText, tripRefText } from '@/shared/trips/tripRefText'
import { useNow } from '@/shared/trips/tripTimes'
import { loadCurrentUser, userName } from '@/shared/user'
import type { BottomBarAction } from '../components/BottomBar.vue'
import BottomBar from '../components/BottomBar.vue'
import PwaPage from '../components/PwaPage.vue'

/**
 * Просмотр чек-листа на телефоне (Ф-ЧЕК-04): объект, рейс, проверяющий, значения пунктов по разделам; нарушенный
 * пункт выделен, у него — описание, фото и созданная заявка (ссылкой на карточку заявки). Только при связи.
 */
const props = defineProps<{ id: string }>()

const router = useRouter()
const busy = provideBusy()
const detail = shallowRef<CheckListDetail | null>(null)
const error = ref<ShownError | null>(null)

const VIOLATIONS = 'violations'
const filterChoices: ChoiceItem[] = [{ id: VIOLATIONS, text: 'Показать только нарушения' }]

// фильтр списка: [VIOLATIONS] — только нарушения; сбрасывается при смене чек-листа
const filter = ref<ChoiceId[]>([])
watch(
  () => props.id,
  () => (filter.value = []),
)

const rows = computed(() => {
  const all = detail.value ? treeRows(detail.value.items) : []
  if (!filter.value.includes(VIOLATIONS)) return all
  return treeRowsWhere(all, (item) => item.valueKind !== null && valueOf(item.id)?.violation === true)
})
const values = computed(() => new Map((detail.value?.values ?? []).map((v) => [v.checkListTemplateItem, v])))

const subject = computed(() => {
  const d = detail.value
  if (!d) return ''
  if (d.wagon) return wagonShortText(d.wagon)
  if (d.checkedUsr) return userName(d.checkedUsr)
  return d.trip ? tripRefText(d.trip) : ''
})

function valueOf(item: number): CheckListValue | undefined {
  return values.value.get(item)
}

// вложенность разделов — небольшим отступом: на телефоне каждый отступ отнимает ширину у текста
const indent = (depth: number) => ({ paddingLeft: `${depth * 0.5}rem` })

function toneClass(v: CheckListValue): string {
  const tone = valueTone(v.violation, v.checkListItemNorm, answered(v))
  return tone ? `view__name--${tone}` : ''
}

/** Цвет плашки ответа: нет ответа — серая, нарушение — красная, по нормативу в порядке — зелёная, без норматива — серая. */
function answerKind(v: CheckListValue): 'ok' | 'bad' | 'neutral' {
  return valueTone(v.violation, v.checkListItemNorm, answered(v)) || 'neutral'
}

function valueText(v: CheckListValue): string {
  if (v.checkDictValue !== null) {
    const dv = detail.value?.dictValues.find((d) => d.id === v.checkDictValue)
    return dv ? textOf(dv) : `<<${v.checkDictValue}>>`
  }
  if (v.valueNumber !== null) return numText(v.valueNumber)
  return v.valueString ?? ''
}

const now = useNow()
const editActions: BottomBarAction[] = [{ key: 'edit', title: 'Внести изменения', kind: 'primary' }]

/** Исправить чек-лист: заполнение с сохранёнными значениями; после сохранения — снова просмотр. */
function edit() {
  void router.replace({ name: 'pwa-check-new', query: { edit: props.id } })
}

function photosOf(v: CheckListValue): RequestPhoto[] {
  return v.photos.map((p) => ({ id: String(p), url: checkPhotoUrl(p) }))
}

async function load() {
  error.value = null
  try {
    const [d] = await busy.run(
      Promise.all([fetchCheckList(Number(props.id)), dictManager.prepareDict('WagonType'), loadCurrentUser()]),
    )
    detail.value = d
  } catch (e) {
    error.value = shownError(e)
  }
}
void load()

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

.view__item {
  border-bottom: 0.0625rem solid $zs-border-row;
}

.view__name--bad {
  color: $zs-error-fg;
}

.view__name--ok {
  color: $zs-ok-fg;
}

.view__line {
  display: flex;
  justify-content: space-between;
  gap: 0.8rem;
}

.view__value {
  font-weight: 600;
  text-align: right;
}

.view__link {
  color: $primary;
  font-size: 0.8rem;
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
