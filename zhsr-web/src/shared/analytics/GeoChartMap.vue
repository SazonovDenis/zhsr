<template>
  <div class="geo-map" :class="{ 'geo-map--still': !interactive }">
    <div ref="mapEl" class="geo-map__map" />
    <MapLegend v-if="interactive && legend" ref="legend" :title="t('Рейсы')" :covered="covered">
      <div v-if="listTrips.length === 0">{{ t('Рейсов в пути нет') }}</div>
      <div
        v-for="a in listTrips"
        :key="a.trip.id"
        class="geo-map__active-trip"
        :class="{ 'geo-map__active-trip--pale': isPale(a.trip.id) }"
        @mouseenter="hovered = a.trip.id"
        @mouseleave="unhover(a.trip.id)"
      >
        <TrainArrow :bearing="a.spot?.bearing ?? 0" :state="arrowState(a)" />
        <span>{{ tripTitle(a.route, a.trip) }}</span>
        <span>
          {{ a.trip.train.name }}<template v-if="started(a)">, {{ delayText(a.spot!.delay) }}</template>
        </span>
      </div>
    </MapLegend>
    <!-- значки поездов: Vue рисует стрелку внутри метки Leaflet -->
    <Teleport v-for="[id, el] in trainIcons" :key="id" :to="el">
      <TrainArrow
        class="geo-map__arrow"
        :bearing="bearings.get(id) ?? 0"
        :state="bearings.has(id) ? 'active' : 'done'"
      />
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import 'leaflet/dist/leaflet.css'

import type {
  DivIconOptions,
  LatLng,
  LatLngBounds,
  Map as LeafletMap,
  Marker,
  Point,
  Polyline,
  Renderer,
} from 'leaflet'
import {
  canvas,
  divIcon,
  latLngBounds,
  map as createMap,
  marker as iconMarker,
  point,
  polyline,
  tileLayer,
} from 'leaflet'
import { computed, onBeforeUnmount, onMounted, ref, shallowReactive, shallowRef, useTemplateRef, watch } from 'vue'

import type { GeoChart, GeoRoute, GeoTrip } from '@/api/analytics'
import { t } from '@/boot/i18n'
import { isMobile } from '@/shared/device'
import { icons as appIcons } from '@/shared/icons/icons'
import { formatDateTime } from '@/shared/format'
import type { ExpectedTrain, TrackPiece } from './geoTrack'
import { delayStep, routePart, stepColor, trackPieces, valueAt } from './geoTrack'
import type { CycleIcon, TripPhase, TrainSpot } from './geoCycle'
import {
  BOLD_BEG,
  BOLD_END,
  cycleItems,
  groupIcons,
  iconHint,
  knownKm,
  trainAt,
  tripPhase,
  tripTimes,
} from './geoCycle'
import { LAYER_DONE } from './geoLayers'
import type { LegendRect } from './MapLegend.vue'
import MapLegend from './MapLegend.vue'
import TrainArrow from './TrainArrow.vue'

/**
 * Карта диаграммы «Текущие рейсы» и экрана состава поезда: бледно-серые линии маршрутов, ход рейсов с начала периода до
 * момента minute (цвет участка — отклонение от расписания, рейсы накладываются), поезда, а на подвижной карте —
 * вагоны и проводники циклов рейсов вне поезда. До «сейчас» (now) ход — по координатам, после — по расписанию с тем же
 * отставанием, пунктиром. Положения на момент считает geoCycle.
 *
 * Поезд рейса в пути — стрелка по ходу в кружке, на конце маршрута, до отправления и после прибытия — кружок. До
 * отправления поезд виден с начала цикла (в миниатюре не виден), после прибытия — пока впереди записи цикла; законченный
 * цикл виден только при слое LAYER_DONE, одним поездом.
 * Значок вагонов и проводников — прямоугольник с числом и иконкой; стоит со сдвигом от места на экране: ждущие прицепки
 * (жёлтые) — на один значок правее поезда, отцепленные (зелёные) — за ними, а без них — на их месте, проводники
 * (белые) — под ждущими; красный — прицепка или отцепка по плану, который не отмечен в срок; бледный — вагона на этой
 * станции нет, он в другом рейсе. В подсказке — фраза (кто чего ждёт или откуда отцеплен, где и какой рейс) и под ней
 * только вагоны или ФИО; у разных мест и рейсов — свои фразы.
 * Значок вагонов и проводников, чьё место на экране заметно занято поездом или другим значком, сдвигается в ближайшее
 * свободное; небольшое пересечение допускается. Поезда не сдвигаются.
 * Справочники Station, Depot, Yard готовит владелец.
 * В плашке (MapLegend) — показанные рейсы; она отходит от их маршрутов. Рейс под мышкой (линия, поезд или строка списка)
 * выделен: остальные исчезают с карты и бледнеют в списке, если показано больше одного.
 *
 * Клик по значку поезда на карте — событие trip с рейсом.
 *
 * Размер карты задаёт владелец. Маршруты с полями вокруг вписываются в карту, пока пользователь не сменил масштаб или
 * место. interactive = false — неподвижная карта ячейки дашборда: подсказки и выделение рейса есть, списка рейсов нет.
 * legend = false — списка рейсов нет и у подвижной карты.
 * trainHint = false — подсказки над поездом нет.
 * fitMarks — при вписывании в карту помещаются и поезда со значками, и карта вписывается заново, когда они сменились.
 * pathKm — место поезда задаёт владелец: поезда рейсов не рисуются, а известный по координатам ход рейсов — весь, но не
 * дальше этого километра маршрута; если он впереди известного, остаток до него — пунктиром.
 * expected — поезд в заданном месте: стрелкой по ходу или, без направления, кружком.
 * icons — значки вагонов и проводников, посчитанные владельцем, вместо значков рейсов.
 */
const props = withDefaults(
  defineProps<{
    chart: GeoChart
    /** момент: минуты от начала периода */
    minute: number
    /** «сейчас» — время загрузки данных: минуты от начала периода */
    now: number
    /** включённые отключаемые слои — LAYER_* */
    layers: number[]
    interactive: boolean
    legend?: boolean
    trainHint?: boolean
    expected?: ExpectedTrain | null
    icons?: CycleIcon[] | null
    fitMarks?: boolean
    /** место поезда задаёт владелец (expected), а ход рейсов — до этого километра */
    pathKm?: number | null
  }>(),
  { legend: true, trainHint: true, expected: null, icons: null, fitMarks: false, pathKm: null },
)

const emit = defineEmits<{
  /** клик по значку поезда рейса; на телефоне события нет */
  trip: [trip: GeoTrip]
}>()

/** id значка expected: не пересекается с id рейсов. */
const EXPECTED_ID = 0

const mapEl = shallowRef<HTMLElement | null>(null)
const legendRef = useTemplateRef<InstanceType<typeof MapLegend>>('legend')

/** Время по минуте от начала периода, мс. */
const timeOf = (minute: number) => new Date(props.chart.from).getTime() + minute * 60000

/** Рейс на момент: фаза цикла и место поезда. */
interface TripView {
  trip: GeoTrip
  route: GeoRoute
  phase: TripPhase
  spot: TrainSpot | null
}

const trips = computed<TripView[]>(() => {
  const routes = new Map(props.chart.routes.map((r) => [r.id, r]))
  const at = timeOf(props.minute)
  const now = timeOf(props.now)
  return props.chart.trips.flatMap((trip) => {
    const route = routes.get(trip.route)
    if (!route) return []
    return [
      { trip, route, phase: tripPhase(tripTimes(trip, now), at), spot: trainAt(props.chart, route, trip, at, now) },
    ]
  })
})

function isShown(a: TripView): boolean {
  if (a.phase === 'hidden') return false
  if (a.phase === 'prep') return props.interactive
  return a.phase !== 'done' || props.layers.includes(LAYER_DONE)
}

/** Рейсы на карте. */
const shownTrips = computed(() => trips.value.filter(isShown))
/** Рейсы списка: все рейсы периода, если показаны законченные, иначе рейсы на карте. */
const listTrips = computed(() => (props.layers.includes(LAYER_DONE) ? trips.value : shownTrips.value))
/** Направления значков со стрелкой по id рейса и EXPECTED_ID; остальные значки — кружки. */
const bearings = computed(() => {
  const res = new Map<number, number>()
  for (const a of shownTrips.value) {
    const bearing = a.phase === 'active' ? (a.spot?.bearing ?? null) : null
    if (bearing !== null) res.set(a.trip.id, bearing)
  }
  const bearing = props.expected?.bearing ?? null
  if (bearing !== null) res.set(EXPECTED_ID, bearing)
  return res
})

function arrowState(a: TripView): 'active' | 'done' | 'future' {
  if (a.phase === 'hidden' || a.phase === 'prep') return 'future'
  return bearings.value.has(a.trip.id) ? 'active' : 'done'
}

/** Рейс отправлен на момент: есть отставание или опережение. */
const started = (a: TripView) => !!a.spot && a.phase !== 'hidden' && a.phase !== 'prep'

/** Рейс под мышкой — id; null — нет. */
const hovered = ref<number | null>(null)

/** Выделенный рейс: под мышкой, и показано рейсов больше одного; null — выделения нет. */
const focused = computed(() => {
  const list = shownTrips.value
  return list.length > 1 && list.some((a) => a.trip.id === hovered.value) ? hovered.value : null
})

function isPale(trip: number): boolean {
  return focused.value !== null && focused.value !== trip
}

// мышь могла уже перейти на другой рейс
function unhover(trip: number) {
  if (hovered.value === trip) hovered.value = null
}

/** Элементы меток поездов по id рейса. */
const trainIcons = shallowReactive(new Map<number, HTMLElement>())

let leaflet: LeafletMap | null = null
let lineRenderer: Renderer | null = null
let resizeObserver: ResizeObserver | null = null
let bounds: LatLngBounds | null = null
// пользователь сам менял масштаб или место: карта больше не вписывает маршруты
let touched = false
// карта уже вписана с метками (fitMarks): дальше она двигается только ради заметной перемены
let fitted = false
// метки, под которые карта вписывалась в прошлый раз: их виды и места
let fittedMarks = ''
let frame = 0
let legendFrame = 0

const routeLines: Polyline[] = []
/** Пунктир хода после «сейчас» по id рейса. */
const forecasts = new Map<number, Polyline>()

/** Нарисованная часть хода рейса; line — null, пока момент не дошёл до неё. */
interface DrawnPiece {
  route: GeoRoute
  trip: GeoTrip
  piece: TrackPiece
  line: Polyline | null
  /** до какого километра нарисована */
  drawnKm: number
  /** рейс не прибыл */
  active: boolean
}
let pieces: DrawnPiece[] = []
const markers = new Map<string, Marker>()
/** Размер метки и её желаемый сдвиг от места, пикселей экрана. */
const boxes = new Map<Marker, MarkBox>()
// тексты подсказок точек на текущий момент
const hints = new Map<string, () => string>()

/** Размер из CSS-переменной корня (в rem), пикселей экрана: Leaflet берёт толщину и радиус числом. */
function cssPx(name: string): number {
  const el = mapEl.value
  if (!el) return 0
  const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
  const px = parseFloat(getComputedStyle(el).getPropertyValue(name)) * rem
  return Number.isFinite(px) ? px : 0
}

function cssColor(name: string): string {
  return mapEl.value ? getComputedStyle(mapEl.value).getPropertyValue(name).trim() : ''
}

/** Данные сменились: маршруты и части хода рейсов — заново. */
function rebuild() {
  const m = leaflet
  if (!m || !lineRenderer) return
  for (const line of routeLines.splice(0)) line.remove()
  for (const line of forecasts.values()) line.remove()
  forecasts.clear()
  for (const p of pieces) p.line?.remove()
  pieces = []
  for (const marker of markers.values()) marker.remove()
  markers.clear()
  boxes.clear()
  trainIcons.clear()

  const all = latLngBounds([])
  for (const route of props.chart.routes) {
    const path = route.points.map((p): [number, number] => [p[1], p[0]])
    if (path.length < 2) continue
    path.forEach((p) => all.extend(p))
    routeLines.push(
      polyline(path, {
        renderer: lineRenderer,
        interactive: false,
        color: cssColor('--geo-route'),
        weight: cssPx('--geo-route-width'),
      }).addTo(m),
    )
  }
  const routes = new Map(props.chart.routes.map((r) => [r.id, r]))
  for (const trip of props.chart.trips) {
    const route = routes.get(trip.route)
    if (!route) continue
    for (const piece of trackPieces(trip)) pieces.push({ route, trip, piece, line: null, drawnKm: 0, active: false })
  }
  bounds = all.isValid() ? all : null
  // с fitMarks вписывает update — когда метки уже нарисованы
  if (!props.fitMarks) fit()
  update()
}

/** Сколько раз поля вписывания расширяются под метки. */
const FIT_PASSES = 4

/** Уже вписанную карту ради меньшего не двигают: обрезание меток — до такой доли ширины значка. */
const CUT_SHARE = 0.4
/** Изменение масштаба меньше этого не стоит перемены вида. */
const ZOOM_STEP = 0.75
/** Сдвиг карты меньше этой доли её меньшей стороны не стоит перемены вида. */
const MOVE_SHARE = 0.2

/** Насколько метки rects выходят за края карты размера size, пикселей, по сторонам. */
function overflow(rects: Rect[], size: { x: number; y: number }) {
  return {
    left: Math.max(0, ...rects.map((r) => HINT_GAP - (r.x - r.width / 2))),
    top: Math.max(0, ...rects.map((r) => HINT_GAP - (r.y - r.height / 2))),
    right: Math.max(0, ...rects.map((r) => r.x + r.width / 2 + HINT_GAP - size.x)),
    bottom: Math.max(0, ...rects.map((r) => r.y + r.height / 2 + HINT_GAP - size.y)),
  }
}

/** Вид карты: середина и масштаб. */
interface View {
  center: LatLng
  zoom: number
}

/** Точка места на карте вида view, пикселей от её левого верхнего угла; саму карту не трогает. */
function projector(m: LeafletMap, view: View): (place: LatLng) => Point {
  const origin = m.project(view.center, view.zoom).subtract(m.getSize().divideBy(2))
  return (place) => m.project(place, view.zoom).subtract(origin)
}

/** Вид, в котором area занимает всё место внутри полей pad, пикселей; карту не трогает. */
function viewOf(m: LeafletMap, area: LatLngBounds, pad: Record<'left' | 'top' | 'right' | 'bottom', number>): View {
  const zoom = Math.min(m.getBoundsZoom(area, false, point(pad.left + pad.right, pad.top + pad.bottom)), m.getMaxZoom())
  const middle = m
    .project(area.getSouthWest(), zoom)
    .add(m.project(area.getNorthEast(), zoom))
    .divideBy(2)
    .add(point(pad.right - pad.left, pad.bottom - pad.top).divideBy(2))
  return { center: m.unproject(middle, zoom), zoom }
}

/**
 * Вид, в который помещаются area и метки вместе с их значками: значки сдвинуты от мест в пикселях экрана и от масштаба не
 * зависят, поэтому поля расширяются там, где значки выходят за край.
 */
function marksView(m: LeafletMap, area: LatLngBounds): View {
  const padding = cssPx('--geo-fit-padding')
  const pad = { left: padding, top: padding, right: padding, bottom: padding }
  for (let pass = 0; pass < FIT_PASSES; pass++) {
    const over = overflow(
      arrange(projector(m, viewOf(m, area, pad))).map((x) => x.rect),
      m.getSize(),
    )
    if (!over.left && !over.top && !over.right && !over.bottom) break
    pad.left += over.left
    pad.top += over.top
    pad.right += over.right
    pad.bottom += over.bottom
  }
  return viewOf(m, area, pad)
}

/**
 * Вписать маршруты; у карты без размера (страница не разложена) масштаб не считается — впишет ResizeObserver.
 *
 * С fitMarks вписываются и метки. Уже вписанную карту мелкими движениями не двигают и вовсе не трогают, пока ни одна
 * метка не обрезана больше чем на CUT_SHARE значка и новый вид отличается от нынешнего лишь мелким масштабированием или
 * сдвигом (пустое место лучше). После того как пользователь сам менял вид (touched), карта двигается, только если
 * сменились метки и какая-то обрезана больше чем на CUT_SHARE значка.
 */
function fit(marksChanged = false) {
  const m = leaflet
  // вид, выбранный пользователем, меняется только ради новых меток, которые в него не помещаются
  if (!m || !bounds || (touched && !(props.fitMarks && marksChanged))) return
  const size = m.getSize()
  if (size.x === 0 || size.y === 0) return
  if (!props.fitMarks) {
    const padding = cssPx('--geo-fit-padding')
    m.fitBounds(bounds, { padding: [padding, padding], animate: false })
    return
  }
  const area = latLngBounds(bounds.getSouthWest(), bounds.getNorthEast())
  for (const marker of markers.values()) area.extend(marker.getLatLng())
  if (fitted) {
    const current = { center: m.getCenter(), zoom: m.getZoom() }
    const cut = Math.max(
      ...Object.values(
        overflow(
          arrange(projector(m, current)).map((x) => x.rect),
          size,
        ),
      ),
    )
    if (cut <= cssPx('--geo-icon-width') * CUT_SHARE) {
      if (touched) return
      const next = marksView(m, area)
      const moved = projector(m, current)(next.center).distanceTo(size.divideBy(2))
      if (Math.abs(next.zoom - current.zoom) < ZOOM_STEP && moved < Math.min(size.x, size.y) * MOVE_SHARE) return
    }
  }
  const next = marksView(m, area)
  fitted = true
  m.setView(next.center, next.zoom, { animate: false })
  layout()
}

function update() {
  if (!leaflet) return
  drawPieces()
  drawForecasts()
  drawMarkers()
  // метки сменились — вписать заново; пока пользователь не менял масштаб и место
  if (!props.fitMarks) return
  const marks = [...markers].map(([key, marker]) => `${key}@${marker.getLatLng().toString()}`).join()
  const changed = marks !== fittedMarks
  fittedMarks = marks
  fit(changed)
}

/** Перерисовка не чаще кадра экрана: бегунок двигают быстрее. */
function scheduleUpdate() {
  if (frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    update()
  })
}

function drawPieces() {
  const m = leaflet!
  // рейс закончился или нарисован законченный — рейсы в пути снова поднимаются над ним
  let moved = false
  const views = new Map(trips.value.map((a) => [a.trip.id, a]))
  const placed = props.pathKm !== null
  // ход по координатам — не позже «сейчас»; с pathKm — весь известный, до этого километра
  const known = placed ? props.now : Math.min(props.minute, props.now)
  const limitKm = props.pathKm ?? Infinity
  for (const p of pieces) {
    const { piece } = p
    const view = views.get(p.trip.id)
    const hidden = !view || (!placed && (!isShown(view) || isPale(p.trip.id)))
    if (hidden || piece.from >= known || piece.fromKm >= limitKm) {
      p.line?.remove()
      p.line = null
      continue
    }
    const active = view.phase === 'active' || view.phase === 'prep'
    if (p.line && p.active !== active) {
      // рейсы в пути — поверх законченных
      if (active) p.line.bringToFront()
      else moved = true
    }
    p.active = active
    const reached = piece.to <= known ? piece.toKm : (valueAt(p.trip.track, known)?.[0] ?? piece.fromKm)
    const toKm = Math.min(reached, limitKm)
    if (p.line && p.drawnKm === toKm) continue
    const path = routePart(p.route, piece.fromKm, toKm)
    if (p.line) {
      p.line.setLatLngs(path)
    } else {
      p.line = tripLine(p.trip.id, path, stepColor(piece.step)).addTo(m)
      if (!active) moved = true
    }
    p.drawnKm = toKm
  }
  if (moved) for (const p of pieces) if (p.active) p.line?.bringToFront()
}

/** Участок хода рейса; dash — пунктир. */
function tripLine(trip: number, path: [number, number][], color: string, dash = false): Polyline {
  const weight = cssPx('--geo-trip-width')
  return polyline(path, {
    renderer: lineRenderer!,
    bubblingMouseEvents: true,
    color,
    opacity: 1,
    weight,
    // соседние части не накладываются концами: иначе на стыках видны тёмные точки
    lineCap: 'butt',
    ...(dash ? { dashArray: `${weight * 2} ${weight}` } : {}),
  })
    .on('mouseover', () => (hovered.value = trip))
    .on('mouseout', () => unhover(trip))
}

/**
 * Будущий путь неприбывших рейсов — от известного по координатам места пунктиром, цветом отставания: до места поезда на
 * момент после «сейчас», а с pathKm — до этого километра, если он впереди известного.
 */
function forecastTargets(): { trip: GeoTrip; route: GeoRoute; km: number; delay: number }[] {
  const km = props.pathKm
  if (km !== null) {
    const now = timeOf(props.now)
    return trips.value.map(({ trip, route }) => ({ trip, route, km, delay: tripTimes(trip, now).lag }))
  }
  if (props.minute <= props.now) return []
  return shownTrips.value
    .filter((a) => a.spot && a.phase !== 'prep' && !isPale(a.trip.id))
    .map(({ trip, route, spot }) => ({ trip, route, km: spot!.km, delay: spot!.delay }))
}

function drawForecasts() {
  const used = new Set<number>()
  for (const { trip, route, km, delay } of forecastTargets()) {
    if (trip.arrivedDt || km <= knownKm(trip)) continue
    const path = routePart(route, knownKm(trip), km)
    if (path.length < 2) continue
    used.add(trip.id)
    const line = forecasts.get(trip.id)
    if (line) line.setLatLngs(path)
    else forecasts.set(trip.id, tripLine(trip.id, path, stepColor(delayStep(delay)), true).addTo(leaflet!))
  }
  for (const [id, line] of forecasts) {
    if (used.has(id)) continue
    line.remove()
    forecasts.delete(id)
  }
}

/** Значки вагонов и проводников: заданные владельцем или рейсов на карте, кроме законченных. */
const shownIcons = computed<CycleIcon[]>(() => {
  if (props.icons) return props.icons
  // в миниатюре значков нет
  if (!props.interactive) return []
  const at = timeOf(props.minute)
  const now = timeOf(props.now)
  return groupIcons(
    shownTrips.value
      .filter((a) => a.phase !== 'done' && !isPale(a.trip.id))
      .flatMap((a) => cycleItems(props.chart, a.trip, at, props.minute > props.now, now)),
  )
})

function drawMarkers() {
  const used = new Set<string>()
  drawTrains(used)
  for (const icon of shownIcons.value) showIcon(used, icon)
  for (const [key, marker] of markers) {
    if (used.has(key)) continue
    boxes.delete(marker)
    marker.remove()
    markers.delete(key)
    if (key.startsWith('train:')) trainIcons.delete(Number(key.slice(6)))
  }
  layout()
}

/** Место метки на экране: прямоугольник и сдвиг от желаемого места. */
interface Placed {
  marker: Marker
  box: MarkBox
  rect: Rect
  shift: [number, number]
}

/**
 * Расставить метки на карте, где место стоит в точке at(место): поезда — всегда на своём месте, и несколько рейсов в
 * одном месте стоят друг на друге; значок вагонов и проводников, чьё желаемое место занято, сдвигается в ближайшее
 * свободное. Порядок — по ключу: при перерисовке значки не меняются местами.
 */
function arrange(at: (place: LatLng) => Point): Placed[] {
  const order = [...markers].sort(
    ([a], [b]) => Number(a.startsWith('icon:')) - Number(b.startsWith('icon:')) || a.localeCompare(b),
  )
  const res: Placed[] = []
  for (const [key, marker] of order) {
    const box = boxes.get(marker)
    if (!box || !marker.getElement()) continue
    const spot = at(marker.getLatLng())
    const shift: [number, number] = key.startsWith('train:')
      ? [0, 0]
      : freeShift(
          { x: spot.x + box.dx, y: spot.y + box.dy },
          box,
          res.map((x) => x.rect),
        )
    const rect = {
      x: spot.x + box.dx + shift[0],
      y: spot.y + box.dy + shift[1],
      width: box.width,
      height: box.height,
    }
    res.push({ marker, box, rect, shift })
  }
  return res
}

/** Поставить метки по их местам на нынешней карте. */
function layout() {
  const m = leaflet
  if (!m) return
  for (const { marker, box, shift } of arrange((place) => m.latLngToContainerPoint(place))) {
    const el = marker.getElement()!
    const [x, y] = [box.dx + shift[0], box.dy + shift[1]]
    // Leaflet ставит метку transform-ом, а сдвиг от места — отступами
    el.style.marginLeft = `${x - box.width / 2}px`
    el.style.marginTop = `${y - box.height / 2}px`
    const options = marker.options.icon?.options as DivIconOptions
    options.tooltipAnchor = [x + (box.tooltipRight ? box.width / 2 : 0), y]
  }
}

function drawTrains(used: Set<string>) {
  const expected = props.expected
  if (expected) showTrain(used, EXPECTED_ID, expected.at, () => '')
  if (props.pathKm !== null) return
  for (const a of shownTrips.value) {
    const { trip, route, spot } = a
    if (!spot || isPale(trip.id)) continue
    showTrain(used, trip.id, spot.at, () =>
      [
        `${BOLD_BEG}${tripTitle(route, trip)}${BOLD_END}`,
        trip.train.name,
        ...(a.phase === 'prep' ? [] : [delayText(spot.delay)]),
      ].join('\n'),
    )
    // рейсы в пути — поверх законченных
    markers.get(`train:${trip.id}`)?.setZIndexOffset(a.phase === 'active' ? 10000 : 0)
  }
}

/**
 * Подпись рейса: «1/2 • Астана — Алматы • 24.11 14:17». Дата — плановое отправление, как у подписи рейса в остальном
 * приложении (tripRefDatedText): поезд ходит регулярно, номер и маршрут не отличают один рейс от другого.
 */
function tripTitle(route: GeoRoute, trip: GeoTrip): string {
  return `${route.trainNumber} • ${route.name} • ${formatDateTime(trip.plannedDt)}`
}

function delayText(delay: number): string {
  const n = Math.round(Math.abs(delay))
  if (n === 0) return t('по графику')
  return delay > 0 ? t('отставание {n} мин', { n }) : t('опережение {n} мин', { n })
}

/** Маршруты показанных рейсов; рейсов нет — все. */
const legendRoutes = computed(() => {
  const ids = new Set(shownTrips.value.map((a) => a.route.id))
  return ids.size ? props.chart.routes.filter((r) => ids.has(r.id)) : props.chart.routes
})

/** Длина маршрутов внутри прямоугольника карты, пикселей. */
function covered(rect: LegendRect): number {
  const m = leaflet
  if (!m) return 0
  let length = 0
  for (const route of legendRoutes.value) {
    let previous: { x: number; y: number } | null = null
    for (const p of route.points) {
      const point = m.latLngToContainerPoint([p[1], p[0]])
      if (previous) length += lengthInside(previous, point, rect)
      previous = point
    }
  }
  return length
}

/** Длина части отрезка внутри прямоугольника. */
function lengthInside(a: { x: number; y: number }, b: { x: number; y: number }, rect: LegendRect): number {
  const dx = b.x - a.x
  const dy = b.y - a.y
  let from = 0
  let to = 1
  for (const [step, gap] of [
    [-dx, a.x - rect.left],
    [dx, rect.left + rect.width - a.x],
    [-dy, a.y - rect.top],
    [dy, rect.top + rect.height - a.y],
  ] as const) {
    if (step === 0) {
      if (gap < 0) return 0
    } else if (step < 0) {
      from = Math.max(from, gap / step)
    } else {
      to = Math.min(to, gap / step)
    }
  }
  return from < to ? Math.hypot(dx, dy) * (to - from) : 0
}

function checkLegend() {
  legendRef.value?.check()
}

/** Проверка плашки не чаще кадра экрана: карту двигают быстрее. */
function scheduleLegend() {
  if (legendFrame) return
  legendFrame = requestAnimationFrame(() => {
    legendFrame = 0
    checkLegend()
  })
}

/** Знак в начале строки подсказки: строка — заголовок. */
const HEAD = '\u0001'

/** Знак перед второй колонкой строки подсказки. */
const COLUMN = '\t'

/** Строка подсказки элементом: части между BOLD_BEG и BOLD_END — жирным. */
function hintLine(className: string, line: string): HTMLElement {
  const div = document.createElement('div')
  div.className = className
  for (const [i, part] of line.split(BOLD_BEG).entries()) {
    const [strong, rest] = i === 0 ? ['', part] : part.split(BOLD_END)
    if (strong) {
      const b = document.createElement('b')
      b.textContent = strong
      div.append(b)
    }
    if (rest) div.append(rest)
  }
  return div
}

/** Подсказка метки: первая строка и строки с HEAD — заголовки, остальные — сведения. */
function bindHint(marker: Marker, key: string) {
  marker.bindTooltip(
    () => {
      const el = document.createElement('div')
      el.className = 'geo-map__hint-rows'
      for (const [i, line] of (hints.get(key)?.() ?? '').split('\n').entries()) {
        const head = i === 0 || line.startsWith(HEAD)
        const columns = line.replace(HEAD, '').split(COLUMN)
        for (const text of columns) {
          const cell = hintLine(head ? 'geo-map__hint-name' : 'geo-map__hint-line', text)
          // строка без второй колонки — во всю ширину
          if (columns.length === 1) cell.classList.add('geo-map__hint-wide')
          el.appendChild(cell)
        }
      }
      return el
    },
    { direction: 'auto', className: 'geo-map__hint' },
  )
  marker.on('tooltipopen', (e) => fitHint(e.tooltip.getElement(), marker.getElement()))
  marker.on('tooltipclose', () => showZoom(true))
}

/** Прямоугольник краями, пикселей от левого верхнего угла карты. */
interface Edges {
  left: number
  top: number
  right: number
  bottom: number
}

const crosses = (a: Edges, b: Edges) => a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

/** Отступ подсказки от краёв карты, плашки и метки, пикселей экрана. */
const HINT_GAP = 4

/**
 * Открытую подсказку оставить внутри карты и вне плашки рейсов. Leaflet ставит подсказку сбоку от метки; не помещается —
 * она сдвигается отступами внутрь карты (шире карты не бывает: строки переносятся). Накрыла плашку или свою метку —
 * поднимается или сдвигается в ближайшее свободное место.
 */
function fitHint(hint: HTMLElement | undefined, mark: HTMLElement | undefined) {
  const box = mapEl.value
  if (!hint || !box) return
  hint.style.marginLeft = ''
  hint.style.marginTop = ''
  hint.style.maxWidth = `${box.clientWidth - 2 * HINT_GAP}px`
  const origin = box.getBoundingClientRect()
  const edges = (el: Element): Edges => {
    const r = el.getBoundingClientRect()
    return {
      left: r.left - origin.left,
      top: r.top - origin.top,
      right: r.right - origin.left,
      bottom: r.bottom - origin.top,
    }
  }
  const start = edges(hint)
  const width = start.right - start.left
  const height = start.bottom - start.top
  const inside = (value: number, size: number, limit: number) =>
    Math.max(HINT_GAP, Math.min(value, limit - size - HINT_GAP))
  const at = (left: number, top: number): Edges => {
    const x = inside(left, width, box.clientWidth)
    const y = inside(top, height, box.clientHeight)
    return { left: x, top: y, right: x + width, bottom: y + height }
  }
  const legendEl = legendRef.value?.$el as HTMLElement | undefined
  const busy = [legendEl, mark].flatMap((el) => (el ? [edges(el)] : []))
  const first = at(start.left, start.top)
  // кандидаты: на своём месте, над и под занятым, правее и левее него
  const candidates = [
    first,
    ...busy.flatMap((b) => [
      at(first.left, b.top - height - HINT_GAP),
      at(b.right + HINT_GAP, first.top),
      at(b.left - width - HINT_GAP, first.top),
      at(first.left, b.bottom + HINT_GAP),
    ]),
  ]
  const distance = (r: Edges) => Math.hypot(r.left - start.left, r.top - start.top)
  const free = candidates.filter((r) => !busy.some((b) => crosses(r, b)))
  const best = free.sort((a, b) => distance(a) - distance(b))[0] ?? first
  const style = getComputedStyle(hint)
  hint.style.marginLeft = `${parseFloat(style.marginLeft) + best.left - start.left}px`
  hint.style.marginTop = `${parseFloat(style.marginTop) + best.top - start.top}px`
  const zoom = zoomEl()
  showZoom(!zoom || !crosses(best, edges(zoom)))
}

const zoomEl = () => mapEl.value?.querySelector<HTMLElement>('.leaflet-control-zoom') ?? null

/**
 * Подсказка — поверх кнопок масштаба. Слой подсказок Leaflet целиком ниже слоя кнопок, поэтому накрытые подсказкой
 * кнопки на время прячутся.
 */
function showZoom(shown: boolean) {
  const zoom = zoomEl()
  if (zoom) zoom.style.visibility = shown ? '' : 'hidden'
}

/** Прямоугольник на экране: центр и размер. */
interface Rect {
  x: number
  y: number
  width: number
  height: number
}

interface MarkBox {
  width: number
  height: number
  dx: number
  dy: number
  /** подсказка — от правого края метки, а не от середины */
  tooltipRight: boolean
}

/** Сколько шагов сетки в каждую сторону ищется свободное место. */
const SHIFT_STEPS = 24

/** Сдвиги сетки от ближних к дальним; из равных — сначала вправо и вниз. */
const SHIFTS: [number, number][] = Array.from({ length: (2 * SHIFT_STEPS + 1) ** 2 }, (_, i): [number, number] => [
  (i % (2 * SHIFT_STEPS + 1)) - SHIFT_STEPS,
  Math.floor(i / (2 * SHIFT_STEPS + 1)) - SHIFT_STEPS,
]).sort((a, b) => a[0] ** 2 + a[1] ** 2 - b[0] ** 2 - b[1] ** 2 || b[0] - a[0] || b[1] - a[1])

/** Небольшое пересечение меток — не наложение: доля меньшей ширины и высоты, на которую метки могут заходить. */
const OVERLAP_X = 0.35
const OVERLAP_Y = 0.15

/** Метки накрывают друг друга заметно: и по ширине больше чем на OVERLAP_X, и по высоте больше чем на OVERLAP_Y. */
function covers(a: Rect, b: Rect): boolean {
  const over = (distance: number, x: number, y: number, share: number) =>
    (x + y) / 2 - distance > Math.min(x, y) * share
  return (
    over(Math.abs(a.x - b.x), a.width, b.width, OVERLAP_X) && over(Math.abs(a.y - b.y), a.height, b.height, OVERLAP_Y)
  )
}

/** Ближайший сдвиг, при котором метка не накрывает заметно расставленные; свободного нет — без сдвига. */
function freeShift(center: { x: number; y: number }, size: MarkBox, placed: Rect[]): [number, number] {
  // шаг мельче допустимого пересечения не нужен, крупный оставлял бы щели
  const step = size.height / 8
  for (const [i, j] of SHIFTS) {
    const rect = { x: center.x + i * step, y: center.y + j * step, width: size.width, height: size.height }
    if (!placed.some((r) => covers(r, rect))) return [i * step, j * step]
  }
  return [0, 0]
}

/** Поставить поезд: новый или уже стоящий — на новое место. */
function showTrain(used: Set<string>, id: number, place: [number, number], text: () => string) {
  const key = `train:${id}`
  used.add(key)
  hints.set(key, text)
  const shown = markers.get(key)
  if (shown) {
    shown.setLatLng(place)
    return
  }
  const marker = trainMarker(id, place)
  markers.set(key, marker)
  if (props.trainHint) bindHint(marker, key)
}

/** Поставить значок вагонов или проводников. */
function showIcon(used: Set<string>, icon: CycleIcon) {
  const key = `icon:${icon.key}`
  used.add(key)
  hints.set(key, () =>
    iconHint(icon)
      .map((line) => (line.head ? HEAD : '') + line.text + (line.time ? COLUMN + line.time : ''))
      .join('\n'),
  )
  let marker = markers.get(key)
  if (!marker) {
    marker = iconAt(icon)
    markers.set(key, marker)
    bindHint(marker, key)
  }
  const box = boxes.get(marker)
  if (box) box.dx = iconShift(icon)
  const el = marker.getElement()
  // разметка — своя: число и иконка из набора приложения
  if (el) el.innerHTML = `<span>${icon.items.length}</span>${iconGlyph(icon.kind)}`
}

/** Иконка значка: у вагонов — вагон приложения, у проводников — человек (Material Icons «man»). */
function iconGlyph(kind: CycleIcon['kind']): string {
  return kind === 'usr'
    ? '<i class="material-icons geo-map__man" aria-hidden="true">man</i>'
    : `<span class="geo-map__wagon-glyph" aria-hidden="true">${appIcons.wagons}</span>`
}

/**
 * Желаемый сдвиг значка вправо от места, пикселей экрана: значки вагонов одного места стоят за поездом подряд — ждущие
 * прицепки, затем отцепленные, у каждых сначала без просрочки; пустое место под отсутствующий не оставляется.
 * Проводники — под первым.
 */
function iconShift(icon: CycleIcon): number {
  const width = cssPx('--geo-icon-width')
  const first = (cssPx('--geo-train-size') + width) / 2
  if (icon.kind === 'usr') return first
  const order = (x: CycleIcon) => (x.kind === 'wait' ? 0 : 2) + Number(x.overdue)
  const before = shownIcons.value.filter(
    (x) => x.kind !== 'usr' && x.pale === icon.pale && String(x.at) === String(icon.at) && order(x) < order(icon),
  )
  return first + before.length * width
}

/**
 * Метка значка: сдвиг от места — в пикселях экрана, от масштаба карты не зависит. Ждущие прицепки — на значок правее
 * поезда, отцепленные — за ними (iconShift); проводники — ниже, бледные — выше.
 */
function iconAt(icon: CycleIcon): Marker {
  const width = cssPx('--geo-icon-width')
  const height = cssPx('--geo-icon-height')
  const dx = iconShift(icon)
  const dy = icon.kind === 'usr' ? height : icon.pale ? -height : 0
  const res = iconMarker(icon.at, {
    pane: 'geo-icon',
    icon: divIcon({
      className: [
        'geo-map__icon',
        `geo-map__icon--${icon.overdue ? 'overdue' : icon.kind}`,
        ...(icon.pale ? ['geo-map__icon--pale'] : []),
      ].join(' '),
      iconSize: [width, height],
    }),
    keyboard: false,
  }).addTo(leaflet!)
  boxes.set(res, { width, height, dx, dy, tooltipRight: false })
  return res
}

/**
 * Клик по значку поезда. event останавливается: в миниатюре клик не доходит до ячейки дашборда, которая открыла бы
 * раскрытую диаграмму. На телефоне экрана поезда нет — клик идёт дальше.
 */
function openTrip(id: number, event: MouseEvent) {
  const trip = props.chart.trips.find((x) => x.id === id)
  if (isMobile.value || !trip) return
  event.stopPropagation()
  emit('trip', trip)
}

function trainMarker(trip: number, place: [number, number]): Marker {
  const size = cssPx('--geo-train-size')
  const res = iconMarker(place, {
    pane: 'geo-train',
    icon: divIcon({ className: 'geo-map__train', iconSize: [size, size], tooltipAnchor: [size / 2, 0] }),
    keyboard: false,
  })
    .on('mouseover', () => (hovered.value = trip))
    .on('mouseout', () => unhover(trip))
    .on('click', (e) => openTrip(trip, e.originalEvent))
    .addTo(leaflet!)
  boxes.set(res, { width: size, height: size, dx: 0, dy: 0, tooltipRight: true })
  const el = res.getElement()
  if (el) trainIcons.set(trip, el)
  return res
}

onMounted(() => {
  if (!mapEl.value) return
  // capture: кнопки масштаба Leaflet не пускают события дальше
  for (const type of ['mousedown', 'wheel', 'touchstart', 'keydown']) {
    mapEl.value.addEventListener(type, () => (touched = true), { capture: true, passive: true })
  }
  const still = !props.interactive
  const m = createMap(mapEl.value, {
    center: [48, 67],
    zoom: 5,
    zoomSnap: 0.25,
    zoomControl: !still,
    dragging: !still,
    scrollWheelZoom: !still,
    doubleClickZoom: false,
    boxZoom: !still,
    touchZoom: !still,
    keyboard: !still,
  })
  leaflet = m
  m.attributionControl.setPrefix(false)
  tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    className: 'geo-map__tiles',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  }).addTo(m)
  // метки — выше линий; значки вагонов и проводников — выше поездов
  ;['train', 'icon'].forEach((kind, i) => {
    m.createPane(`geo-${kind}`).style.zIndex = String(450 + i)
  })
  lineRenderer = canvas({ padding: 0.3 })
  // move — во время движения карты, не только после него
  m.on('move moveend zoomend resize', scheduleLegend)
  // расстояния между местами на экране меняются с масштабом
  m.on('zoomend viewreset', layout)
  rebuild()
  resizeObserver = new ResizeObserver(() => {
    m.invalidateSize()
    fit()
  })
  resizeObserver.observe(mapEl.value)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  cancelAnimationFrame(legendFrame)
  resizeObserver?.disconnect()
  leaflet?.remove()
  leaflet = null
  fitted = false
  fittedMarks = ''
  markers.clear()
  boxes.clear()
  forecasts.clear()
  trainIcons.clear()
  pieces = []
})

// набор маршрутов сменился — плашка проверяется после своей перерисовки
watch(() => legendRoutes.value.map((r) => r.id).join(), checkLegend, { flush: 'post' })
watch(() => props.chart, rebuild)
watch(
  [
    () => props.minute,
    () => props.now,
    () => props.layers,
    () => props.expected,
    () => props.icons,
    () => props.pathKm,
    focused,
  ],
  scheduleUpdate,
)
</script>

<style scoped lang="scss">
.geo-map {
  --geo-route: #b8b8b8;
  --geo-route-width: 0.3rem;
  --geo-trip-width: 0.3rem;
  --geo-train-size: 1.3rem;
  --geo-icon-width: 2.4rem;
  --geo-icon-height: 1.3rem;
  // поля вокруг маршрутов, когда карта вписывает их
  --geo-fit-padding: 2rem;

  position: relative;
  // слои Leaflet не поднимаются над окнами и шапкой страницы
  isolation: isolate;
  overflow: hidden;
  border: 0.1rem solid $zs-border;
  border-radius: 0.3rem;
  background: $zs-bg-header;
}

.geo-map__map {
  position: absolute;
  inset: 0;
  font-family: $zs-font;
}

.geo-map__active-trip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  column-gap: 0.5rem;
  transition: opacity 0.2s;

  &--pale {
    opacity: 0.3;
  }
}

// ячейка дашборда: нажатие на подпись карты тоже открывает диаграмму
.geo-map--still :deep(.leaflet-control-container) {
  pointer-events: none;
}

// подложка бледная: цвета линий читаются
.geo-map :deep(.geo-map__tiles) {
  opacity: 0.6;
  filter: grayscale(1);
}

.train-arrow.geo-map__arrow {
  width: 100%;
  height: 100%;
}

.geo-map :deep(.geo-map__icon) {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.2rem;
  border: 0.1rem solid #fff;
  border-radius: 0.3rem;
  font-family: $zs-font;
  font-size: 0.8rem;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.geo-map :deep(.geo-map__icon--wait) {
  background: #ffc53d;
  color: #22303f;
}

.geo-map :deep(.geo-map__icon--left) {
  background: #12967c;
  color: #fff;
}

// план не отмечен — красным, как в журнале
.geo-map :deep(.geo-map__icon--overdue) {
  background: $zs-error-fg;
  color: #fff;
}

.geo-map :deep(.geo-map__icon--usr) {
  border-color: #22303f;
  background: #fff;
  color: #22303f;
}

.geo-map :deep(.geo-map__wagon-glyph) {
  display: inline-flex;
  width: 1rem;
  height: 1rem;

  svg {
    width: 100%;
    height: 100%;
  }
}

.geo-map :deep(.geo-map__man) {
  font-size: 1rem;
}

.geo-map :deep(.geo-map__icon--pale) {
  opacity: 0.45;
}

// подсказка — как на карте маршрута (RouteMap): плашка без стрелки; строки переносятся, только если не помещаются в
// ширину карты (её задаёт fitHint)
.geo-map :deep(.geo-map__hint) {
  width: max-content;
  padding: 0.4rem 0.5rem;
  border: 0.1rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
  box-shadow: 0 0.1rem 0.5rem rgb(0 0 0 / 15%);
  color: $zs-text;
  font-size: $zs-route-name-size;
  font-weight: 400;
  line-height: 1.35;
  white-space: normal;

  &::before {
    display: none;
  }
}

.geo-map :deep(.geo-map__hint b) {
  font-weight: $zs-route-stop-weight;
}

.geo-map :deep(.geo-map__hint-line) {
  font-size: 0.8rem;
}

// две колонки списка: ширину широких строк забирает вторая, и она стоит сразу за самым длинным названием
.geo-map :deep(.geo-map__hint-rows) {
  display: grid;
  grid-template-columns: max-content 1fr;
  column-gap: 0.8rem;
}

.geo-map :deep(.geo-map__hint-wide) {
  grid-column: 1 / -1;
}
</style>
