<template>
  <section class="train-moment">
    <div class="train-moment__head">
      <span class="train-moment__title">{{ title }}</span>
      <StatusBadge :tone="plan ? 'wait' : 'ok'">{{ plan ? t('ожидаемый по плану') : t('по факту') }}</StatusBadge>
      <span class="train-moment__muted">{{ t('{n} вагонов', rows.length) }}</span>
    </div>

    <DataTable
      :columns="columns"
      :rows="rows"
      row-key="key"
      :selected="highlight"
      :row-menu="withMenu ? rowMenu : null"
      empty="В поезде нет вагонов"
      class="train-moment__table"
    />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TrainCard, TrainChange, TrainWagon } from '@/api/trains'
import { t } from '@/boot/i18n'
import StatusBadge from '@/shared/components/StatusBadge.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import { actionsMenu } from '@/shared/components/table/rowMenu'
import type { RowAction } from '@/shared/components/table/rowMenu'
import type { Column } from '@/shared/components/table/types'
import { defineColumns } from '@/shared/components/table/types'
import { formatDateTime } from '@/shared/format'
import type { MomentWagon } from '@/shared/trains/trainJournal'
import {
  changeKindText,
  compositionAfter,
  currentComposition,
  isAttach,
  isFact,
  isWaiting,
  journalOf,
  planMoment,
} from '@/shared/trains/trainJournal'
import { wagonsColumns } from '../wagons/wagonsColumns'

/** Строка состава на момент: вагон поезда с полями колонок списка вагонов. */
interface MomentRow extends TrainWagon {
  key: number
  seq: number
  /** ожидающие планы вагона после момента словами */
  plans: string
}

/**
 * Состав поезда на момент (Ф-ПОЕ-02): без выбранной записи журнала — текущий состав по факту; запись факта — состав по
 * факту после неё; запись плана — ожидаемый состав после неё. Показан строго состав на момент: вагона, которого нет в
 * поезде, в таблице нет. Колонки «Номер», «Тип», «Расположение», «Состояние» — как в списке вагонов. В режиме журнала «Настоящее» —
 * ещё колонка «По плану» (ожидающие планы вагона после момента) и меню вагона «Отцепить вагон»; в
 * режиме «Прошлое» меню — только у текущего состава. Вагон, которого касается запись, выделен. Справочники Station,
 * Depot, Yard, LocationKind, WagonType, WagonModel готовит страница.
 *
 * @prop card        поезд
 * @prop changeId    выбранная запись журнала; null — сейчас
 * @prop future      журнал в режиме «Настоящее»
 * @prop coupleReason почему не прицепить и не отцепить; пусто — можно
 */
const props = defineProps<{ card: TrainCard; changeId: number | null; future: boolean; coupleReason: string }>()
const emit = defineEmits<{ detach: [wagon: TrainWagon, factAllowed: boolean, plan: boolean, at: string | null] }>()

const change = computed<TrainChange | null>(() => props.card.changes.find((c) => c.id === props.changeId) ?? null)
const plan = computed(() => !!change.value && isWaiting(change.value))
/** Момент состава, ISO: у факта — его время, у плана — время применения (не раньше последнего факта). */
const moment = computed(() => {
  const c = change.value
  if (!c) return null
  return isFact(c) ? c.dt : planMoment(props.card, c)
})

const after = computed<MomentWagon[]>(() =>
  change.value ? compositionAfter(props.card, change.value) : currentComposition(props.card),
)

const title = computed(() => {
  const c = change.value
  if (!c) return t('Состав сейчас')
  return t('Состав после записи: {what} {number}, {time}', {
    what: changeKindText(c).toLowerCase(),
    number: c.wagon.number,
    time: formatDateTime(moment.value),
  })
})

/** Ожидающие планы после момента: после выбранного плана в порядке журнала; у текущего состава — все. */
const plansAfter = computed<TrainChange[]>(() => {
  const plans = journalOf(props.card).plans
  if (!plan.value) return plans
  return plans.slice(plans.findIndex((c) => c.id === props.changeId) + 1)
})

/** Ожидающие планы вагона после момента словами. */
function plansOf(wagon: number): string {
  return plansAfter.value
    .filter((c) => c.wagon.id === wagon)
    .map((c) => t(isAttach(c) ? 'прицепка {time}' : 'отцепка {time}', { time: formatDateTime(c.dt) }))
    .join(', ')
}

const rows = computed<MomentRow[]>(() =>
  after.value.map((m) => ({
    ...m.wagon,
    key: m.wagon.wagon,
    seq: m.state.seq,
    plans: props.future ? plansOf(m.wagon.wagon) : '',
  })),
)

/** Выделенный вагон: вагон выбранной записи. */
const highlight = computed(() => change.value?.wagon.id ?? null)

// «Номер», «Тип», «Расположение», «Состояние» — те же, что в списке вагонов; строка вагона поезда несёт их поля
const wagonColumns = (wagonsColumns as unknown as Column<MomentRow>[])
  .filter(
    (c) =>
      c.field === 'number' || c.field === 'wagonType' || c.field === 'locationType' || c.field === 'wagonStatusType',
  )
  .map((c) => ({ ...c, sortable: false }))

const columns = computed(() => [
  ...defineColumns<MomentRow>([{ field: 'seq', title: '№', width: '3rem' }]),
  ...wagonColumns,
  ...(props.future ? defineColumns<MomentRow>([{ field: 'plans', title: 'По плану' }]) : []),
])

/** Меню вагона: в будущем — всегда, в прошлом — только у текущего состава. */
const withMenu = computed(() => props.future || !change.value)
/** Вагоны в поезде сейчас по факту: их можно отцепить фактом. */
const factWagons = computed(() => new Set(currentComposition(props.card).map((m) => m.wagon.wagon)))

const DETACH = 'Отцепить вагон'

/**
 * Одно действие «Отцепить вагон»: окно с выбором «Планом» / «Фактом». Окно открыто планом, если выбран план журнала или
 * вагона нет в поезде сейчас; у выбранного плана время по умолчанию — его время.
 */
function actionsOf(r: MomentRow): RowAction[] {
  const fact = factWagons.value.has(r.wagon)
  const at = plan.value ? moment.value : null
  return [{ label: DETACH, reason: props.coupleReason, run: () => emit('detach', r, fact, plan.value || !fact, at) }]
}
const rowMenu = actionsMenu<MomentRow>([DETACH], actionsOf)
</script>

<style scoped lang="scss">
.train-moment {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  min-width: 0;
  min-height: 0;
}

.train-moment__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.train-moment__title {
  font-size: 0.9rem;
  font-weight: 600;
}

.train-moment__muted {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.train-moment__table {
  flex: 1;
  min-height: 0;
}
</style>
