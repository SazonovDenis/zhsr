<template>
  <section class="train-moment">
    <div class="train-moment__head">
      <span class="train-moment__title">{{ title }}</span>
      <StatusBadge :tone="plan ? 'wait' : 'ok'">{{
        momentBadgeText(plan, moment ?? new Date().toISOString())
      }}</StatusBadge>
      <span class="train-moment__muted">{{ t('{n} вагонов', rows.length) }}</span>
    </div>

    <ErrorBox v-if="equipError" :error="equipError" :retry="loadEquip" />

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
import { computed, provide, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { EquipListShortage } from '@/api/equip'
import { fetchEquipShortages } from '@/api/equip'
import { fetchTripResponsibles, saveTripResponsibles } from '@/api/trips'
import type { TrainCard, TrainChange, TrainWagon } from '@/api/trains'
import { t } from '@/boot/i18n'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import StatusBadge from '@/shared/components/StatusBadge.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import { actionsMenu } from '@/shared/components/table/rowMenu'
import type { RowAction } from '@/shared/components/table/rowMenu'
import type { Column } from '@/shared/components/table/types'
import { defineColumns } from '@/shared/components/table/types'
import { CrewDuty, LocationType, RoleType } from '@/shared/constants/systemDicts'
import type { ShownError } from '@/shared/errors'
import { notifyError, shownError } from '@/shared/errors'
import { TripResponsibleLayer } from '@/shared/constants/TripResponsibleLayer'
import { isoDateTime } from '@/shared/format'
import type { JournalMark, MomentWagon } from '@/shared/trains/trainJournal'
import {
  changeKey,
  changeKindText,
  compositionAfter,
  compositionAt,
  currentComposition,
  expectedAt,
  isFact,
  isSeq,
  isWaiting,
  journalOf,
  momentBadgeText,
  movedBySeq,
  planMoment,
} from '@/shared/trains/trainJournal'
import { TrainWagonKind } from '@/shared/constants/TrainWagonChange'
import { currentUser, hasRole } from '@/shared/user'
import { pickWagonUsers } from '../trips/pickWagonUsers'
import { wagonsColumns } from '../wagons/wagonsColumns'
import CellMomentPlans from './cells/CellMomentPlans.vue'
import CellWagonReadiness from './cells/CellWagonReadiness.vue'
import type { TripMoment } from './tripMoment'
import { MOMENT_PLANS, TRIP_CONDUCTORS, TRIP_EQUIP, TRIP_TRANSFER } from './tripMoment'

/** Строка состава на момент: вагон поезда с полями колонок списка вагонов. */
interface MomentRow extends TrainWagon {
  key: number
  seq: number
  /** ожидающие планы вагона после момента */
  plans: TrainChange[]
  /** выбранная запись журнала (changeKey): её план в ячейке выделен */
  selectedPlan: string | null
}

/**
 * Состав поезда на момент (Ф-ПОЕ-02): без выбранной записи журнала — текущий состав по факту, а при выбранном рейсе —
 * состав на отправление рейса (до отправления — ожидаемый по плану, после — по факту); запись факта — состав по факту
 * после неё; запись плана — ожидаемый состав после неё. Выбран рейс —
 * колонка «Готовность» каждого вагона: на другой станции, нужна передача на станцию (со ссылкой «Оформить передачу») или в другом поезде; проводники не по норме —
 * предупреждение и ссылка «Назначить»; опись экипировки не
 * проводилась — предупреждение, в ней недостача — предупреждение и ссылка «Опись». Показан строго состав на момент: вагона, которого нет в
 * поезде, в таблице нет. Колонки «Номер», «Тип», «Расположение», «Состояние» — как в списке вагонов. В режиме журнала «Настоящее» —
 * ещё колонка «Запланировано» (ожидающие планы вагона после момента) и меню вагона «Отцепить вагон»; в
 * режиме «Прошлое» меню — только у текущего состава. Вагон, которого касается запись, выделен. Справочники Station,
 * Depot, Yard, LocationKind, WagonType, WagonModel готовит страница.
 *
 * @prop card        поезд
 * @prop selected    выбранная запись журнала (changeKey); null — сейчас
 * @prop future      журнал в режиме «Настоящее»
 * @prop coupleReason почему не прицепить и не отцепить; пусто — можно
 * @prop trip        выбранный рейс поезда; null — рейс не выбран
 * @prop now         при выбранном рейсе показан текущий состав
 * @prop mark        выбранная отметка рейса в журнале: состав на её момент (факт — по факту, план — ожидаемый); null —
 *                   не выбрана
 */
const props = withDefaults(
  defineProps<{
    card: TrainCard
    selected: string | null
    future: boolean
    coupleReason: string
    trip?: TripMoment | null
    now?: boolean
    mark?: JournalMark | null
  }>(),
  { trip: null, mark: null, now: false },
)
const emit = defineEmits<{
  detach: [wagon: TrainWagon, factAllowed: boolean, plan: boolean, at: string | null]
  select: [key: string]
  'pick-trip': [id: number]
  /** ответственные за вагон рейса изменены */
  'crew-changed': []
}>()
provide(MOMENT_PLANS, { select: (key) => emit('select', key), pickTrip: (id) => emit('pick-trip', id) })

const change = computed<TrainChange | null>(
  () => props.card.changes.find((c) => changeKey(c) === props.selected) ?? null,
)
/** Показан состав на момент отметки рейса: отметка выбрана, запись журнала — нет. */
const atMark = computed(() => !change.value && !!props.mark)
/** Показан состав на отправление выбранного рейса: рейс выбран, запись журнала и отметка — нет. */
const atTrip = computed(() => !change.value && !props.mark && !props.now && !!props.trip)
const plan = computed(() => {
  if (change.value) return isWaiting(change.value)
  if (atMark.value) return !props.mark!.fact
  return atTrip.value && !props.trip!.fact
})
/** Момент состава, ISO: у факта — его время, у плана — время применения (не раньше последнего факта). */
const moment = computed(() => {
  const c = change.value
  if (!c) return props.mark ? props.mark.dt : atTrip.value ? props.trip!.at : null
  return isFact(c) ? c.dt : planMoment(props.card, c)
})

const after = computed<MomentWagon[]>(() => {
  if (change.value) return compositionAfter(props.card, change.value)
  const mark = props.mark
  if (mark) return mark.fact ? compositionAt(props.card, TrainWagonKind.FACT, mark.dt) : expectedAt(props.card, mark.dt)
  const trip = atTrip.value ? props.trip : null
  if (!trip) return currentComposition(props.card)
  return trip.fact ? compositionAt(props.card, TrainWagonKind.FACT, trip.at) : expectedAt(props.card, trip.at)
})

const title = computed(() => {
  const c = change.value
  if (!c && props.mark)
    return t(
      props.mark.kind === 'departure'
        ? 'Состав на момент отправления рейса {trip}'
        : 'Состав на момент прибытия рейса {trip}',
      { trip: props.mark.trip.trainNumber },
    )
  if (atTrip.value) return t('Состав на момент отправления рейса {trip}', { trip: props.trip!.number })
  if (!c) return t('Состав сейчас')
  if (isSeq(c)) return t('Состав после смены порядка вагонов')
  return t('Состав после записи: {what} {number}', { what: changeKindText(c).toLowerCase(), number: c.wagon!.number })
})

/** Ожидающие планы после момента: после выбранного плана в порядке журнала; у текущего состава — все. */
const plansAfter = computed<TrainChange[]>(() => {
  const plans = journalOf(props.card).plans
  if (atTrip.value || atMark.value) {
    const at = new Date(moment.value!).getTime()
    return plans.filter((c) => new Date(planMoment(props.card, c)).getTime() > at)
  }
  if (!plan.value) return plans
  return plans.slice(plans.findIndex((c) => changeKey(c) === props.selected) + 1)
})

/** Ожидающие планы после момента с вагонами, которых они касаются: у смены порядка — кто меняет позицию. */
const plansWagons = computed(() =>
  plansAfter.value.map((c) => ({ c, wagons: c.wagon ? new Set([c.wagon.id]) : movedBySeq(props.card, c) })),
)

/** Ожидающие планы вагона после момента. */
function plansOf(wagon: number): TrainChange[] {
  return plansWagons.value.filter((x) => x.wagons.has(wagon)).map((x) => x.c)
}

const rows = computed<MomentRow[]>(() =>
  after.value.map((m) => ({
    ...m.wagon,
    key: m.wagon.wagon,
    seq: m.state.seq,
    plans: props.future ? plansOf(m.wagon.wagon) : [],
    selectedPlan: props.selected,
  })),
)

/** Выделенный вагон: вагон выбранной прицепки или отцепки. */
const highlight = computed(() => change.value?.wagon?.id ?? null)

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

/** Колонка «Готовность»: всегда, когда выбран рейс, — при любом показанном моменте состава. */
const withReadiness = computed(() => !!props.trip)

const columns = computed(() => [
  ...defineColumns<MomentRow>([{ field: 'seq', title: '№', width: '2rem' }]),
  ...wagonColumns,
  ...(withReadiness.value
    ? defineColumns<MomentRow>([
        { field: 'key', title: 'Готовность', width: 'minmax(7rem, 1fr)', cell: CellWagonReadiness },
      ])
    : []),
  ...(props.future
    ? defineColumns<MomentRow>([
        { field: 'plans', title: 'Запланировано', width: 'minmax(12rem, 1.4fr)', cell: CellMomentPlans },
      ])
    : []),
])

// передачу на станцию оформляют мастер депо, приёмщик и администратор
const router = useRouter()
provide(TRIP_TRANSFER, {
  problems: (w) => props.trip?.problems.filter((p) => p.wagon.id === w.wagon) ?? [],
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

// описи вагонов состава для колонки «Готовность»; null — ещё не загружены
const equipLists = shallowRef<EquipListShortage[] | null>(null)
const equipError = ref<ShownError | null>(null)
/** Вагоны, описи которых нужны, через запятую; пусто — колонки «Готовность» нет или состав пуст. */
const equipWagons = computed(() => (withReadiness.value ? after.value.map((m) => m.wagon.wagon).join(',') : ''))

async function loadEquip() {
  const key = equipWagons.value
  equipError.value = null
  equipLists.value = null
  if (!key) return
  try {
    const lists = await fetchEquipShortages(key.split(',').map(Number))
    if (equipWagons.value === key) equipLists.value = lists
  } catch (e) {
    if (equipWagons.value === key) equipError.value = shownError(e)
  }
}
watch(equipWagons, loadEquip, { immediate: true })

provide(TRIP_EQUIP, {
  lists: (wagon) => equipLists.value?.filter((l) => l.wagon.id === wagon) ?? null,
  // раздел «Описи»: поиск по номеру вагона, период — день описи, опись выбрана
  open: (list, number) => {
    const day = isoDateTime(new Date(list.createdDt)).slice(0, 10)
    void router.push({ name: 'equip', query: { query: number, from: day, to: day, id: String(list.id) } })
  },
})

/**
 * Ответственные за вагон на рейс: окно выбора, выбор сразу сохраняется: у рейса до отправления — планом на рейс,
 * после отправления — фактом с текущего времени.
 */
async function assignConductors(w: TrainWagon) {
  const trip = props.trip!.id
  try {
    const data = await fetchTripResponsibles(trip, TripResponsibleLayer.TRIP)
    const current = data.wagons.find((x) => x.wagon.id === w.wagon)?.usrs ?? []
    const saved = await pickWagonUsers(data, w.number, current, async (usrs) => {
      await saveTripResponsibles(trip, null, [{ wagon: w.wagon, usrs }], TripResponsibleLayer.TRIP)
    })
    if (saved) emit('crew-changed')
  } catch (e) {
    notifyError(e)
  }
}
provide(TRIP_CONDUCTORS, {
  notOk: (w) => props.trip?.norm.some((n) => n.number === w.number && !n.ok) ?? false,
  reason: () => {
    const u = currentUser.value
    const chief = u?.trip?.id === props.trip?.id && u?.trip?.crewDutyType === CrewDuty.LNP
    if (chief || [RoleType.ADMIN, RoleType.WAGON_PLANNER, RoleType.CREW_PLANNER].some((r) => hasRole(u, r))) return ''
    return t('Ответственных за вагоны меняют начальник поезда рейса и планировщики')
  },
  assign: (w) => void assignConductors(w),
})

/** Меню вагона: в будущем — всегда, в прошлом — только у текущего состава. */
const withMenu = computed(() => props.future || (!change.value && !atTrip.value && !props.mark))
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
