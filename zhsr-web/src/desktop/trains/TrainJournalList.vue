<template>
  <section class="train-journal">
    <!-- прошлое — факты: последние или за период; настоящее — текущее состояние и ожидающие планы; выбран рейс — одна
         кнопка «Выбранный рейс», отжатие снимает выбор рейса -->
    <div class="train-journal__modes">
      <CbSegment
        v-if="onlyTrip !== null"
        :model-value="'trip'"
        single
        :values="TRIP_SIDE"
        @update:model-value="emit('clear-trip')"
      />
      <CbSegment v-else v-model="side" single required :values="SIDES" @change="emit('select', null)" />
      <template v-if="onlyTrip === null && side === 'past'">
        <CbSegment v-model="pastMode" single required :values="PAST_MODES" />
        <CbSegment
          v-if="pastMode === 'period'"
          v-model="period"
          single
          required
          :values="PERIODS"
          :max-button-count="4"
        />
      </template>
    </div>
    <!-- подпись — как подпись состава справа -->
    <div class="train-journal__head">
      <span class="train-journal__title">{{ t('Журнал отцепок и прицепок') }}</span>
    </div>

    <div class="train-journal__box">
      <!-- заголовки колонок — как у таблиц; колонки — как у записи журнала -->
      <div class="train-journal__cols q-py-sm q-pl-md q-pr-xs" role="row">
        <span role="columnheader">{{ t('Время') }}</span>
        <span role="columnheader">{{ t('Событие') }}</span>
      </div>

      <div ref="scrollEl" class="train-journal__list" @keydown="onArrow">
        <!-- настоящее: «Текущее состояние» сверху, под ним планы -->
        <!-- выбран рейс — строки «Текущее состояние» нет: момент по умолчанию — отметка отправления рейса -->
        <JournalItem
          v-if="side === 'future' && onlyTrip === null"
          :change="null"
          :selected="selected === null && selectedMark === null"
          @select="emit('select', null)"
        />
        <div v-if="rows.length === 0" class="train-journal__empty q-px-md q-py-sm">
          {{ onlyTrip !== null ? t('Записей нет') : side === 'past' ? t('Фактов нет') : t('Планов нет') }}
        </div>
        <!-- записи журнала и отметки рейсов (отправление, прибытие) — одной шкалой по времени -->
        <template v-for="(row, i) in rows" :key="row.key">
          <!-- выбран рейс и «сейчас» — в периоде рейса: строка «Текущее состояние» на своём месте по времени -->
          <JournalItem v-if="row.now" :change="null" :selected="nowSelected" @select="emit('select-now')" />
          <JournalMark
            v-else-if="row.mark"
            :mark="row.mark"
            :selected="isMarkSelected(row.mark)"
            @select="emit('select-mark', row.mark)"
          />
          <JournalItem
            v-else-if="row.change"
            :change="row.change"
            :hide-time="i > 0 && rows[i - 1]!.dt === row.dt"
            :selected="selected === row.change.id"
            :has-menu="actionsOf(row.change).length > 0"
            @select="emit('select', row.change.id)"
            @menu="openMenu($event, row.change!)"
            @trip="emit('pick-trip', $event)"
          />
        </template>
        <!-- прошлое: факты по времени, «Текущее состояние» внизу -->
        <JournalItem
          v-if="side === 'past' && onlyTrip === null"
          :change="null"
          :selected="selected === null && selectedMark === null"
          @select="emit('select', null)"
        />
      </div>
    </div>
    <RowMenu ref="menuEl" />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useTemplateRef, watch } from 'vue'

import type { TrainCard, TrainChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import RowMenu from '@/shared/components/table/RowMenu.vue'
import { arrowTarget } from '@/shared/composables/listArrows'
import type { JournalMark as Mark } from '@/shared/trains/trainJournal'
import { nowMinute } from '@/shared/since'
import { changeActions, journalOf, tripMarksOf, tripMarksOfTrip } from '@/shared/trains/trainJournal'
import type { JournalSide } from './journalSide'
import JournalItem from './TrainJournalItem.vue'
import JournalMark from './TrainJournalMark.vue'

/**
 * Журнал поезда (Ф-ПОЕ-02): над ним переключатель «Настоящее» / «Прошлое», под переключателем подпись «Журнал отцепок
 * и прицепок». Прошлое
 * — факты по времени и внизу строка «Текущее состояние»; факты — последние для вагона (у каждого вагона — его
 * последний факт) или за период: неделя, месяц, 3 месяца, год. Время, равное времени записи выше, не повторяется. Настоящее — сверху строка «Текущее состояние», под ней ожидающие планы по времени. В рамке —
 * заголовки колонок «Время» и «Событие». Выбор записи — момент, на который справа показан состав: у факта — по факту,
 * у плана — ожидаемый; «Текущее состояние» — текущий состав. Смена прошлого и будущего выбирает «Текущее состояние».
 * Между записями — отметки рейсов поезда (TrainJournalMark): в прошлом — фактические отправления и прибытия, в
 * настоящем — плановые; клик по отметке — состав на её момент (событие select-mark). У записи — меню действий:
 * «Отметить» и «Отменить план» у плана, «Удалить факт» у факта; недоступные — с причиной. Номер рейса записи — мягкая
 * ссылка: выбрать этот рейс (событие pick-trip).
 * Список прокручен к строке «Текущее состояние». Журнал поезда сервер отдаёт целиком: период отбирает факты здесь.
 *
 * @prop card     поезд
 * @prop selected выбранная запись (train_wagon_change.id); null — «Текущее состояние»
 * @prop only     показывать только эти записи (выбран рейс: записи рейса и записи вне рейса за его период); null —
 *                все
 * @prop selectedMark выбранная отметка рейса (JournalMark.key); null — не выбрана
 * @prop onlyTrip выбранный рейс: одна шкала без деления на настоящее и прошлое — записи only (факты и планы) и отметки
 *                этого рейса (отправление и прибытие — фактические, иначе плановые); «Текущее состояние» — на своём
 *                месте, если сейчас идёт рейс (событие select-now); вместо переключателя — кнопка «Выбранный рейс»,
 *                отжатие — событие clear-trip; null — рейс не выбран
 * @prop nowSelected выбрана строка «Текущее состояние» при выбранном рейсе
 * @model side    настоящее или прошлое
 * @model pastFrom начало периода прошлого (мс) для прошлых рейсов страницы; null — без границы
 */
const props = withDefaults(
  defineProps<{
    card: TrainCard
    selected: number | null
    disable?: boolean
    only?: Set<number> | null
    selectedMark?: string | null
    onlyTrip?: number | null
    nowSelected?: boolean
  }>(),
  { disable: false, only: null, selectedMark: null, onlyTrip: null, nowSelected: false },
)

/** Выделена ли отметка: выбрана; при выбранном рейсе без выбора — его отметка отправления. */
function isMarkSelected(m: Mark): boolean {
  if (props.selectedMark !== null) return props.selectedMark === m.key
  return props.selected === null && !props.nowSelected && props.onlyTrip === m.trip.id && m.kind === 'departure'
}

/** Стрелки вверх и вниз — выбор соседней строки журнала. */
function onArrow(e: KeyboardEvent) {
  const el = arrowTarget(e, '.journal-item, .journal-mark', '.journal-item--selected, .journal-mark--selected')
  el?.focus()
  el?.click()
}

const side = defineModel<JournalSide>('side', { required: true })
const emit = defineEmits<{
  select: [id: number | null]
  'select-mark': [mark: Mark]
  'select-now': []
  'clear-trip': []
  'pick-trip': [trip: number]
  mark: [change: TrainChange]
  cancel: [change: TrainChange]
  undo: [change: TrainChange]
  edit: [change: TrainChange]
}>()

const TRIP_SIDE: ChoiceItem[] = [{ id: 'trip', text: 'Выбранный рейс' }]
const SIDES: ChoiceItem[] = [
  { id: 'future', text: 'Настоящее' },
  { id: 'past', text: 'Прошлое' },
]
const PAST_MODES: ChoiceItem[] = [
  { id: 'last', text: 'Последние для вагона' },
  { id: 'period', text: 'За период' },
]
/** Периоды «За период»: сколько дней назад от сейчас. */
const PERIOD_DAYS: Record<string, number> = { week: 7, month: 31, quarter: 92, year: 366 }
const PERIODS: ChoiceItem[] = [
  { id: 'week', text: 'Неделя' },
  { id: 'month', text: 'Месяц' },
  { id: 'quarter', text: '3 месяца' },
  { id: 'year', text: 'Год' },
]

const pastMode = ref<string>('last')
const period = ref<string>('week')
/** Начало периода прошлого (мс) — для прошлых рейсов страницы; «Последние для вагона» — без границы (null). */
const pastFrom = defineModel<number | null>('pastFrom', { default: null })
watch(
  [pastMode, period],
  () =>
    (pastFrom.value = pastMode.value === 'period' ? Date.now() - (PERIOD_DAYS[period.value] ?? 7) * 86_400_000 : null),
  { immediate: true },
)

const journal = computed(() => {
  const all = journalOf(props.card)
  const only = props.only
  if (!only) return all
  return { facts: all.facts.filter((c) => only.has(c.id)), plans: all.plans.filter((c) => only.has(c.id)) }
})
/** Показанные записи: прошлое — факты последние или за период, настоящее — текущее состояние и ожидающие планы. */
const shown = computed<TrainChange[]>(() => {
  // выбран рейс — факты и планы одной шкалой
  if (props.onlyTrip !== null) return [...journal.value.facts, ...journal.value.plans]
  if (side.value === 'future') return journal.value.plans
  const facts = journal.value.facts
  if (pastMode.value === 'last') {
    // у каждого вагона — его последний факт; порядок по времени сохраняется
    const last = new Map(facts.map((c) => [c.wagon.id, c.id]))
    return facts.filter((c) => last.get(c.wagon.id) === c.id)
  }
  const from = Date.now() - (PERIOD_DAYS[period.value] ?? 7) * 86_400_000
  return facts.filter((c) => new Date(c.dt).getTime() >= from)
})

/** Показанные отметки рейсов: те же сторона и период, что у записей; выбран рейс — только его отметки. */
const marks = computed<Mark[]>(() => {
  if (props.onlyTrip !== null) return tripMarksOfTrip(props.card, props.onlyTrip)
  const past = side.value === 'past'
  let list = tripMarksOf(props.card, past)
  if (props.onlyTrip !== null) list = list.filter((m) => m.trip.id === props.onlyTrip)
  if (past && pastMode.value === 'period') {
    const from = Date.now() - (PERIOD_DAYS[period.value] ?? 7) * 86_400_000
    list = list.filter((m) => new Date(m.dt).getTime() >= from)
  }
  return list
})

/** Строка журнала: запись, отметка рейса или (при выбранном рейсе) «Текущее состояние». */
interface JournalRow {
  key: string
  dt: string
  change: TrainChange | null
  mark: Mark | null
  now: boolean
}

/**
 * Записи и отметки одной шкалой по времени. В один момент с отправлением записи — перед отправлением: это прицепки и
 * отцепки на станции отправления, без отметки которых рейс не отправляется; в один момент с прибытием записи — после
 * прибытия. Выбран рейс и сейчас он идёт (между
 * отправлением и прибытием) — ещё строка «Текущее состояние» на своём месте по времени.
 */
const rows = computed<JournalRow[]>(() => {
  const list: JournalRow[] = [
    ...marks.value.map((m) => ({ key: `m${m.key}`, dt: m.dt, change: null, mark: m, now: false })),
    ...shown.value.map((c) => ({ key: `c${c.id}`, dt: c.dt, change: c, mark: null, now: false })),
  ]
  if (props.onlyTrip !== null) {
    const dep = marks.value.find((m) => m.kind === 'departure')
    const arr = marks.value.find((m) => m.kind === 'arrival')
    const now = new Date(nowMinute.value)
    if (dep && arr && new Date(dep.dt) <= now && now < new Date(arr.dt))
      list.push({ key: 'now', dt: now.toISOString(), change: null, mark: null, now: true })
  }
  return list.sort((a, b) => new Date(a.dt).getTime() - new Date(b.dt).getTime() || sameTimeOrder(a) - sameTimeOrder(b))
})

/** Порядок строк одного момента: прибытие, затем записи, затем отправление. */
function sameTimeOrder(row: JournalRow): number {
  if (!row.mark) return 0
  return row.mark.kind === 'departure' ? 1 : -1
}

const scrollEl = useTemplateRef<HTMLElement>('scrollEl')
const menuEl = useTemplateRef<InstanceType<typeof RowMenu>>('menuEl')

const actionsOf = (c: TrainChange) =>
  changeActions(c, props.card.changes, {
    mark: (x) => emit('mark', x),
    cancel: (x) => emit('cancel', x),
    undo: (x) => emit('undo', x),
    edit: (x) => emit('edit', x),
  })

function openMenu(e: MouseEvent, c: TrainChange) {
  const items = actionsOf(c).map((a) => ({
    label: a.label,
    reason: a.reason || (props.disable ? t('Идёт другое действие') : ''),
    separator: false,
    run: a.run,
  }))
  void menuEl.value?.show(items, e.clientX, e.clientY, true)
}

/** К строке «Текущее состояние»: в прошлом она внизу, в будущем — вверху. */
async function scrollToNow() {
  await nextTick()
  const list = scrollEl.value
  if (list) list.scrollTop = side.value === 'past' ? list.scrollHeight : 0
}
onMounted(scrollToNow)
watch([side, pastMode, period], scrollToNow)
</script>

<style scoped lang="scss">
.train-journal {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  min-height: 0;
}

// как подпись состава справа (TrainMomentView)
.train-journal__head {
  display: flex;
  flex: none;
  align-items: center;
  gap: 0.6rem;
}

.train-journal__title {
  font-size: 0.9rem;
  font-weight: 600;
}

.train-journal__modes {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.train-journal__box {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

// заголовки колонок — как у DataTable; колонки и отступы — как у записи (TrainJournalItem, с полосой выбора слева)
.train-journal__cols {
  display: grid;
  flex: none;
  grid-template-columns: 5.6rem minmax(0, 1fr) 1.2rem;
  gap: 0.6rem;
  border-left: 0.2rem solid transparent;
  background: $zs-table-head-bg;
  box-shadow: inset 0 -0.0625rem 0 $zs-table-head-border;
  color: $zs-table-head-fg;
  font-size: 0.8rem;
  font-weight: 600;
  white-space: nowrap;
}

.train-journal__list {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.train-journal__empty {
  color: $zs-text-faint;
  font-size: 0.8rem;
}
</style>
