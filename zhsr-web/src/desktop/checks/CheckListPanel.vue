<template>
  <SidePanel
    :empty="checkListId ? '' : 'Выберите проверку в списке'"
    :title="detail ? textOf(detail.checkListTemplate) : ''"
    :subtitle="detail ? formatDateTime(detail.createdDt) : ''"
    @close="emit('close')"
  >
    <LoadSpinner v-if="busy.active.value && !detail" />
    <ErrorBox v-if="error" :error="error" :retry="load" />

    <template v-if="detail">
      <SidePanelFields>
        <span>{{ t('Объект проверки') }}</span>
        <!-- вагон — номер ссылкой: список вагонов с поиском по этому номеру -->
        <span v-if="detail.wagon">
          {{ t('вагон') }}
          <SoftRouteLink
            text="{number}"
            :params="{ number: detail.wagon.number }"
            :to="{ name: 'wagons', query: { number: detail.wagon.number } }"
          />
        </span>
        <span v-else>{{ checkSubjectText(detail) || '—' }}</span>
        <span>{{ t('Рейс') }}</span>
        <span>{{ detail.trip ? tripRefDatedText(detail.trip) : '—' }}</span>
        <span>{{ t('Проверяющий') }}</span>
        <span><UserName :user="detail.createdByUsr" /></span>
        <span>{{ t('Окончена') }}</span>
        <span>{{ formatDateTime(detail.completedDt) }}</span>
        <!-- подпись — по центру строки с кнопкой фильтра -->
        <span class="self-center">{{ t('Нарушений') }}</span>
        <!-- «Показать только нарушения» — в списке только нарушенные пункты; нарушений нет — «нет» и без кнопки -->
        <span class="check-panel__violations">
          <span v-if="detail.violationCount > 0" class="check-panel__bad-text">{{ detail.violationCount }}</span>
          <template v-else>{{ t('нет') }}</template>
          <CbButton v-if="detail.violationCount > 0" v-model="filter" :values="filterChoices" />
        </span>
      </SidePanelFields>

      <!-- исправить — автор в течение 10 минут после сохранения; кнопка исчезает сама, когда срок вышел -->
      <div v-if="listEditable(detail, now)" class="q-mt-md">
        <q-btn unelevated outline no-caps class="zs-soft-border" color="ink" @click="edit">{{
          t('Внести изменения')
        }}</q-btn>
      </div>

      <div class="check-panel__items q-mt-md">
        <template v-for="{ item, depth } in rows" :key="item.id">
          <div
            v-if="item.valueKind === null"
            class="check-panel__section q-pt-sm"
            :style="{ paddingLeft: `${depth}rem` }"
          >
            {{ textOf(item) }}
          </div>
          <div
            v-else-if="valueOf(item.id)"
            class="check-panel__item q-py-xs q-px-xs"
            :style="{ paddingLeft: `${depth}rem` }"
          >
            <!-- нарушение — красное название, без нарушения по нормативу — зелёное -->
            <div class="check-panel__line">
              <span :class="toneClass(valueOf(item.id)!)">{{ textOf(item) }}</span>
              <!-- ответ — в прямоугольной плашке: в порядке — зелёной, нарушение — красной, без норматива и без
                   ответа («—») — серой -->
              <span class="check-panel__value">
                <StatusBadge :tone="answerKind(valueOf(item.id)!)">{{
                  answered(valueOf(item.id)!) ? valueText(valueOf(item.id)!) : '—'
                }}</StatusBadge>
              </span>
            </div>
            <div v-if="valueOf(item.id)!.comment" class="q-mt-xs">{{ valueOf(item.id)!.comment }}</div>
            <!-- заявка — с таким же отступом от описания, как описание от строки пункта -->
            <div v-if="valueOf(item.id)!.repairRequest" class="q-mt-xs">
              <SoftRouteLink
                text="Заявка {number}"
                :params="{ number: valueOf(item.id)!.repairRequest!.number }"
                :to="requestLink(valueOf(item.id)!.repairRequest!)"
              />
            </div>
            <div v-if="valueOf(item.id)!.photos.length > 0" class="check-panel__photos q-mt-xs">
              <a v-for="p in valueOf(item.id)!.photos" :key="p" :href="checkPhotoUrl(p)" target="_blank" rel="noopener">
                <img :src="checkPhotoUrl(p)" :alt="t('Фото')" />
              </a>
            </div>
          </div>
        </template>
      </div>
    </template>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import UserName from '@/shared/components/UserName.vue'
import type { CheckListDetail, CheckListValue, RequestRef } from '@/api/checks'
import { checkPhotoUrl, fetchCheckList } from '@/api/checks'
import { t } from '@/boot/i18n'
import StatusBadge from '@/shared/components/StatusBadge.vue'
import { provideBusy } from '@/shared/busy'
import { numText, textOf } from '@/shared/checks/texts'
import { treeRows, treeRowsWhere } from '@/shared/checks/tree'
import { listEditable } from '@/shared/checks/rights'
import { answered, valueTone } from '@/shared/checks/valueView'
import type { ChoiceId, ChoiceItem } from '@/shared/components/choice/choice'
import CbButton from '@/shared/components/choice/CbButton.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import SoftRouteLink from '@/shared/components/form/SoftRouteLink.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import { useNow } from '@/shared/trips/tripTimes'
import { loadCurrentUser } from '@/shared/user'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import { checkSubjectText } from './checksColumns'

/**
 * Карточка проверки (Ф-ЧЕК-04): объект, рейс, проверяющий, значения пунктов по разделам; нарушенный пункт выделен, у
 * него — описание, фото и созданная заявка. «Показать только нарушения» у числа нарушений оставляет в списке только нарушенные пункты.
 */
const props = defineProps<{ checkListId: number | null }>()
const emit = defineEmits<{ close: [] }>()

const busy = provideBusy()
const detail = shallowRef<CheckListDetail | null>(null)
const error = ref<ShownError | null>(null)

const values = computed(() => new Map((detail.value?.values ?? []).map((v) => [v.checkListTemplateItem, v])))

const VIOLATIONS = 'violations'

// фильтр списка: [VIOLATIONS] — только нарушения; сбрасывается при смене проверки
const filter = ref<ChoiceId[]>([])
watch(
  () => props.checkListId,
  () => (filter.value = []),
)

const filterChoices: ChoiceItem[] = [{ id: VIOLATIONS, text: 'Показать только нарушения' }]

const rows = computed(() => {
  const all = detail.value ? treeRows(detail.value.items) : []
  if (!filter.value.includes(VIOLATIONS)) return all
  return treeRowsWhere(all, (item) => item.valueKind !== null && valueOf(item.id)?.violation === true)
})

const router = useRouter()
const now = useNow()

/** Исправить чек-лист: страница заполнения с сохранёнными значениями. */
function edit() {
  if (detail.value) void router.push({ name: 'check-new', query: { edit: String(detail.value.id) } })
}

function valueOf(item: number): CheckListValue | undefined {
  return values.value.get(item)
}

/** Переход к заявке: список заявок с поиском по номеру, заявка выбрана. */
function requestLink(r: RequestRef) {
  return { name: 'requests', query: { request: String(r.id), number: r.number } }
}

function toneClass(v: CheckListValue): string {
  const tone = valueTone(v.violation, v.checkListItemNorm, answered(v))
  return tone ? `check-panel__name--${tone}` : ''
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

async function load() {
  const id = props.checkListId
  detail.value = null
  error.value = null
  if (id === null) return
  try {
    const [d] = await busy.run(Promise.all([fetchCheckList(id), loadCurrentUser()]))
    if (props.checkListId === id) detail.value = d
  } catch (e) {
    error.value = shownError(e)
  }
}
watch(() => props.checkListId, load, { immediate: true })
</script>

<style scoped lang="scss">
.check-panel__violations {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.check-panel__items {
  display: flex;
  flex-direction: column;
  font-size: 0.8rem;
}

.check-panel__section {
  font-weight: 600;
}

.check-panel__item {
  border-bottom: 0.0625rem solid $zs-border-row;
}

.check-panel__name--bad {
  color: $zs-error-fg;
}

.check-panel__name--ok {
  color: $zs-ok-fg;
}

.check-panel__line {
  display: flex;
  justify-content: space-between;
  gap: 0.8rem;
}

.check-panel__value {
  font-weight: 600;
  text-align: right;
}

.check-panel__bad-text {
  color: $zs-error-fg;
  font-weight: 600;
}

.check-panel__photos {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;

  img {
    width: 3.5rem;
    height: 3.5rem;
    border-radius: 0.3rem;
    object-fit: cover;
  }
}
</style>
