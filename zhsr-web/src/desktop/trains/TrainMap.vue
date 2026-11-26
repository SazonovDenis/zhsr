<template>
  <ErrorBox v-if="error" :error="error" :retry="load" />
  <LoadSpinner v-else-if="!chart" />
  <div v-else class="train-map">
    <GeoChartMap
      class="train-map__map"
      :chart="shown!"
      :minute="minuteOf(badgeAt)"
      :now="minuteOf(loadedAt)"
      :layers="[]"
      :icons="icons"
      :expected="expected"
      :path-km="pathKm"
      :legend="false"
      :train-hint="false"
      fit-marks
      interactive
    />
    <StatusBadge :tone="plan ? 'wait' : 'ok'" class="train-map__badge">
      {{ momentBadgeText(plan, new Date(badgeAt).toISOString()) }}
    </StatusBadge>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'

import type { GeoChart, GeoRoute, GeoTrip } from '@/api/analytics'
import { fetchTrainGeoChart } from '@/api/analytics'
import type { TrainCard, TrainChange } from '@/api/trains'
import GeoChartMap from '@/shared/analytics/GeoChartMap.vue'
import { cycleItems, groupIcons, routeLength, spotAt, trainAt, tripPhase, tripTimes } from '@/shared/analytics/geoCycle'
import type { ExpectedTrain } from '@/shared/analytics/geoTrack'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import StatusBadge from '@/shared/components/StatusBadge.vue'
import { TrainJournalCode } from '@/shared/constants/TrainWagonChange'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { journalOf, momentBadgeText } from '@/shared/trains/trainJournal'
import type { TrainMapView } from './trainMapView'

/**
 * Карта поезда на то, что выбрано на экране, с плашкой «по факту» или «ожидаемый по плану»: поезд и вагоны и проводники
 * цикла рейса вне поезда (geoCycle).
 *
 * Место поезда задаёт выбранное: запись журнала — её станция (в рейсе — остановка: между концами маршрута стрелкой по
 * ходу), отправление и рейс — начало маршрута, прибытие — конец. Пройденный путь виден до конца цикла (pathTrip, pathKm).
 * «Сейчас»: в рейсе — по координатам;
 * вне рейса — станция вагонов, а если они в разных местах — станция последней фактической записи. Данные перечитываются,
 * когда сменилась карточка поезда.
 */
const props = defineProps<{
  card: TrainCard
  view: TrainMapView
  /** показывается ожидаемое по плану */
  plan: boolean
}>()

const chart = shallowRef<GeoChart | null>(null)
const error = ref<ShownError | null>(null)
// «сейчас» — время загрузки данных
const loadedAt = ref(0)
let loadNo = 0

async function load() {
  const no = ++loadNo
  error.value = null
  try {
    const now = Date.now()
    const data = await fetchTrainGeoChart(props.card.id)
    if (no !== loadNo) return
    loadedAt.value = now
    chart.value = data
  } catch (e) {
    if (no !== loadNo) return
    chart.value = null
    error.value = shownError(e)
  }
}
watch(() => props.card, load, { immediate: true })

const ms = (iso: string) => new Date(iso).getTime()

/** Момент карты и плашки: по плану — сам момент, по факту — не позже «сейчас». */
const badgeAt = computed(() => {
  const view = props.view
  if (view.kind === 'now') return loadedAt.value
  return props.plan ? ms(view.at) : Math.min(ms(view.at), loadedAt.value)
})

const minuteOf = (time: number) => (chart.value ? (time - ms(chart.value.from)) / 60000 : 0)

/**
 * Рейс, цикл которого показывается: у записи журнала — в чьих она записях (смена порядка — рейс записи или по времени), у
 * рейса — он, «сейчас» — первый неприбывший, иначе последний.
 */
const trip = computed<GeoTrip | null>(() => {
  const trips = chart.value?.trips ?? []
  const view = props.view
  const last = trips[trips.length - 1] ?? null
  if (view.kind === 'trip') return trips.find((x) => x.id === view.trip) ?? null
  if (view.kind === 'now') return trips.find((x) => !x.arrivedDt) ?? last
  const c = view.change
  const own =
    c.code === TrainJournalCode.TRAIN_WAGON_CHANGE
      ? trips.find((x) => x.changes.some((y) => y.id === c.id))
      : trips.find((x) => x.id === c.trip?.id)
  return own ?? trips.find((x) => tripTimes(x, loadedAt.value).arrival >= ms(view.at)) ?? last
})

/** Записи того же момента после выбранной в журнале: на карте они ещё не действуют. */
function laterThan(c: TrainChange, at: number, cycle: GeoTrip): Set<number> {
  // смена порядка идёт после прицепок и отцепок своего момента
  if (c.code !== TrainJournalCode.TRAIN_WAGON_CHANGE) return new Set()
  return new Set(cycle.changes.filter((x) => ms(x.moment) === at && x.id > c.id).map((x) => x.id))
}

const icons = computed(() => {
  const data = chart.value
  const cycle = trip.value
  const view = props.view
  // «сейчас» после конца последнего цикла показывать нечего
  if (!data || !cycle) return []
  if (view.kind === 'now' && tripPhase(tripTimes(cycle, loadedAt.value), loadedAt.value) === 'done') return []
  const later = view.kind === 'change' ? laterThan(view.change, ms(view.at), cycle) : new Set<number>()
  return groupIcons(cycleItems(data, cycle, badgeAt.value, props.plan, loadedAt.value, later))
})

/** Рейс в пути, выбранный «сейчас»: его поезд карта рисует по координатам. */
const running = computed(() => {
  const x = trip.value
  return props.view.kind === 'now' && x?.departedDt && !x.arrivedDt ? x : null
})

/**
 * Рейс, путь которого виден: при любом выбранном в его цикле; «сейчас» после конца цикла пути нет. У неотправленного
 * хода по координатам нет — только пунктир до места поезда.
 */
const pathTrip = computed(() => {
  const cycle = trip.value
  if (!cycle) return null
  const over = tripPhase(tripTimes(cycle, loadedAt.value), loadedAt.value) === 'done'
  return props.view.kind === 'now' && over ? null : cycle
})

const shown = computed<GeoChart | null>(() =>
  chart.value ? { ...chart.value, trips: pathTrip.value ? [pathTrip.value] : [] } : null,
)

/**
 * До какого километра виден путь, когда место поезда задаёт выбранное: до остановки записи, до начала или конца маршрута,
 * у записи без остановки — до места поезда в её время. null — «сейчас» в рейсе: поезд и путь по координатам.
 */
const pathKm = computed<number | null>(() => {
  if (running.value) return null
  const data = chart.value
  const cycle = pathTrip.value
  const view = props.view
  const route = cycle && data?.routes.find((r) => r.id === cycle.route)
  if (!data || !cycle || !route) return 0
  if (view.kind === 'trip') return view.end ? routeLength(route) : 0
  const stop =
    view.kind === 'change' && view.change.trip?.id === cycle.id
      ? route.stops.find((x) => x.id === view.change.routeStation)
      : undefined
  return stop?.km ?? trainAt(data, route, cycle, Math.min(badgeAt.value, loadedAt.value), loadedAt.value)?.km ?? 0
})

function routeOf(id: number | undefined): GeoRoute | undefined {
  const route = chart.value?.trips.find((x) => x.id === id)?.route
  return chart.value?.routes.find((r) => r.id === route)
}

function atStation(station: number | undefined): ExpectedTrain | null {
  const point = chart.value?.stations.find((x) => x.station === station)?.point
  return point ? { at: [point.y, point.x], bearing: null } : null
}

/** Значок поезда, место которого задаёт выбранное, а не координаты. */
const expected = computed<ExpectedTrain | null>(() => {
  const view = props.view
  if (view.kind === 'trip') {
    const route = routeOf(view.trip)
    return route ? spotAt(route, view.end ? routeLength(route) : 0, false) : null
  }
  if (view.kind === 'change') {
    const c = view.change
    // остановка рейса — по километру: маршрут может проходить станцию дважды
    const route = routeOf(c.trip?.id)
    const stop = route?.stops.find((x) => x.id === c.routeStation)
    return route && stop ? spotAt(route, stop.km, true) : atStation(c.station)
  }
  if (running.value) return null
  const facts = journalOf(props.card).facts
  return atStation(props.card.station ?? facts[facts.length - 1]?.station)
})
</script>

<style scoped lang="scss">
.train-map {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.train-map__map {
  flex: 1;
  min-height: 0;
}

// поверх слоёв Leaflet: карта изолирует их z-index
.train-map__badge {
  position: absolute;
  z-index: 1;
  top: 0.5rem;
  right: 0.5rem;
}
</style>
