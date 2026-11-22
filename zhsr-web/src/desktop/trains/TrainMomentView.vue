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
import { computed, provide } from 'vue'
import { useRouter } from 'vue-router'

import type { TrainCard, TrainChange, TrainWagon } from '@/api/trains'
import { t } from '@/boot/i18n'
import StatusBadge from '@/shared/components/StatusBadge.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import { actionsMenu } from '@/shared/components/table/rowMenu'
import type { RowAction } from '@/shared/components/table/rowMenu'
import type { Column } from '@/shared/components/table/types'
import { defineColumns } from '@/shared/components/table/types'
import { LocationType, RoleType } from '@/shared/constants/systemDicts'
import { formatDateTime } from '@/shared/format'
import type { JournalMark, MomentWagon } from '@/shared/trains/trainJournal'
import {
  changeKindText,
  compositionAfter,
  compositionAt,
  currentComposition,
  expectedAt,
  isAttach,
  isFact,
  isWaiting,
  journalOf,
  planMoment,
} from '@/shared/trains/trainJournal'
import { TrainWagonKind } from '@/shared/constants/TrainWagonChange'
import { currentUser, hasRole } from '@/shared/user'
import { wagonsColumns } from '../wagons/wagonsColumns'
import CellTripAway from './cells/CellTripAway.vue'
import type { TripMoment } from './tripMoment'
import { TRIP_TRANSFER } from './tripMoment'

/** Строка состава на момент: вагон поезда с полями колонок списка вагонов. */
interface MomentRow extends TrainWagon {
  key: number
  seq: number
  /** ожидающие планы вагона после момента словами */
  plans: string
}

/**
 * Состав поезда на момент (Ф-ПОЕ-02): без выбранной записи журнала — текущий состав по факту, а при выбранном рейсе —
 * состав на отправление рейса (до отправления — ожидаемый по плану, после — по факту); запись факта — состав по факту
 * после неё; запись плана — ожидаемый состав после неё. У рейса до отправления вагон не на станции отправления —
 * колонка «К отправлению»: предупреждение и ссылка «Оформить передачу». Показан строго состав на момент: вагона, которого нет в
 * поезде, в таблице нет. Колонки «Номер», «Тип», «Расположение», «Состояние» — как в списке вагонов. В режиме журнала «Настоящее» —
 * ещё колонка «По плану» (ожидающие планы вагона после момента) и меню вагона «Отцепить вагон»; в
 * режиме «Прошлое» меню — только у текущего состава. Вагон, которого касается запись, выделен. Справочники Station,
 * Depot, Yard, LocationKind, WagonType, WagonModel готовит страница.
 *
 * @prop card        поезд
 * @prop changeId    выбранная запись журнала; null — сейчас
 * @prop future      журнал в режиме «Настоящее»
 * @prop coupleReason почему не прицепить и не отцепить; пусто — можно
 * @prop trip        выбранный рейс поезда; null — рейс не выбран
 * @prop mark        выбранная отметка рейса в журнале: состав на её момент (факт — по факту, план — ожидаемый); null —
 *                   не выбрана
 */
const props = withDefaults(
  defineProps<{
    card: TrainCard
    changeId: number | null
    future: boolean
    coupleReason: string
    trip?: TripMoment | null
    mark?: JournalMark | null
  }>(),
  { trip: null, mark: null },
)
const emit = defineEmits<{ detach: [wagon: TrainWagon, factAllowed: boolean, plan: boolean, at: string | null] }>()

const change = computed<TrainChange | null>(() => props.card.changes.find((c) => c.id === props.changeId) ?? null)
/** Показан состав на момент отметки рейса: отметка выбрана, запись журнала — нет. */
const atMark = computed(() => !change.value && !!props.mark)
/** Показан состав на отправление выбранного рейса: рейс выбран, запись журнала и отметка — нет. */
const atTrip = computed(() => !change.value && !props.mark && !!props.trip)
const plan = computed(() => {
  if (change.value) return isWaiting(change.value)
  if (atMark.value) return !props.mark!.fact
  return atTrip.value && !props.trip!.fact
})
/** Момент состава, ISO: у факта — его время, у плана — время применения (не раньше последнего факта). */
const moment = computed(() => {
  const c = change.value
  if (!c) return props.mark ? props.mark.dt : props.trip ? props.trip.at : null
  return isFact(c) ? c.dt : planMoment(props.card, c)
})

const after = computed<MomentWagon[]>(() => {
  if (change.value) return compositionAfter(props.card, change.value)
  const mark = props.mark
  if (mark) return mark.fact ? compositionAt(props.card, TrainWagonKind.FACT, mark.dt) : expectedAt(props.card, mark.dt)
  const trip = props.trip
  if (!trip) return currentComposition(props.card)
  return trip.fact ? compositionAt(props.card, TrainWagonKind.FACT, trip.at) : expectedAt(props.card, trip.at)
})

const title = computed(() => {
  const c = change.value
  if (!c && props.mark)
    return t(
      props.mark.kind === 'departure'
        ? 'Состав на момент отправления рейса {trip}, {time}'
        : 'Состав на момент прибытия рейса {trip}, {time}',
      { trip: props.mark.trip.trainNumber, time: formatDateTime(props.mark.dt) },
    )
  if (!c && props.trip)
    return t('Состав на момент отправления рейса {trip}, {time}', {
      trip: props.trip.number,
      time: formatDateTime(props.trip.at),
    })
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
  if (atTrip.value || atMark.value) {
    const at = new Date(moment.value!).getTime()
    return plans.filter((c) => new Date(planMoment(props.card, c)).getTime() > at)
  }
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
/**
 * Ширины колонок без зависимости от содержимого: каждая строка таблицы — своя сетка, и ширина «по содержимому»
 * (min-content) у строк разная — колонки прыгали бы от строки к строке.
 */
const WIDTHS: Record<string, string> = {
  wagonType: 'minmax(6rem, 0.8fr)',
  locationType: 'minmax(8rem, 1.3fr)',
  wagonStatusType: 'minmax(7rem, 1fr)',
}

const wagonColumns = (wagonsColumns as unknown as Column<MomentRow>[])
  .filter(
    (c) =>
      c.field === 'number' || c.field === 'wagonType' || c.field === 'locationType' || c.field === 'wagonStatusType',
  )
  .map((c) => {
    const width = WIDTHS[c.field as string] ?? c.width
    return width === undefined ? { ...c, sortable: false } : { ...c, sortable: false, width }
  })

/** Колонка «К отправлению»: у рейса до отправления, когда показан состав на его отправление. */
const withAway = computed(() => atTrip.value && !props.trip!.fact && props.trip!.departureStation !== null)

const columns = computed(() => [
  ...defineColumns<MomentRow>([{ field: 'seq', title: '№', width: '3rem' }]),
  ...wagonColumns,
  ...(withAway.value
    ? defineColumns<MomentRow>([
        { field: 'key', title: 'К отправлению', width: 'minmax(7rem, 1fr)', cell: CellTripAway },
      ])
    : []),
  ...(props.future
    ? defineColumns<MomentRow>([{ field: 'plans', title: 'По плану', width: 'minmax(6rem, 0.8fr)' }])
    : []),
])

// передачу на станцию оформляют мастер депо, приёмщик и администратор
const router = useRouter()
provide(TRIP_TRANSFER, {
  away: (w) => w.locationType !== LocationType.STATION || w.place !== props.trip?.departureStation,
  reason: (w) => {
    if (![RoleType.ADMIN, RoleType.MASTER, RoleType.RECEIVER].some((r) => hasRole(currentUser.value, r)))
      return t('Передачу оформляет мастер депо или приёмщик')
    if (w.locationType === LocationType.DEPOT || w.locationType === LocationType.YARD) return ''
    return t('Передать на станцию можно только из депо или парка отстоя')
  },
  open: (w) =>
    void router.push({
      name: 'transfer',
      query: {
        from: w.locationType === LocationType.DEPOT ? `d${w.place}` : `y${w.place}`,
        to: `s${props.trip!.departureStation}`,
        wagons: w.number,
      },
    }),
})

/** Меню вагона: в будущем — всегда, в прошлом — только у текущего состава. */
const withMenu = computed(() => props.future || (!change.value && !props.trip && !props.mark))
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
