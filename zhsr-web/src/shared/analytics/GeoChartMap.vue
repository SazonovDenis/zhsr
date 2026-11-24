<template>
  <div class="geo-map" :class="{ 'geo-map--still': !interactive }">
    <div ref="mapEl" class="geo-map__map" />
    <MapLegend v-if="interactive" ref="legend" :title="t('Рейсы')" :covered="covered">
      <div v-if="listTrips.length === 0">{{ t('Рейсов в пути нет') }}</div>
      <div
        v-for="a in listTrips"
        :key="a.trip.id"
        class="geo-map__active-trip"
        :class="{ 'geo-map__active-trip--pale': isPale(a.trip.id) }"
        @mouseenter="hovered = a.trip.id"
        @mouseleave="unhover(a.trip.id)"
      >
        <TrainArrow v-if="a.value || a.state === 'future'" :bearing="a.bearing" :state="a.state" />
        <span>{{ a.route.trainNumber }} {{ a.route.name }}</span>
        <span>
          {{ a.trip.train.name
          }}<template v-if="a.value && a.state !== 'future'">, {{ delayText(a.value[1]) }}</template>
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

import type { CircleMarker, LatLngBounds, Map as LeafletMap, Marker, Polyline, Renderer } from 'leaflet'
import {
  canvas,
  circleMarker,
  divIcon,
  latLngBounds,
  map as createMap,
  marker as iconMarker,
  polyline,
  tileLayer,
} from 'leaflet'
import { computed, onBeforeUnmount, onMounted, ref, shallowReactive, shallowRef, useTemplateRef, watch } from 'vue'

import type { GeoChart, GeoRoute, GeoTrip } from '@/api/analytics'
import { t } from '@/boot/i18n'
import { formatDateTime } from '@/shared/format'
import type { TrackPiece } from './geoTrack'
import { placeAt, routeBearing, routePart, routePlace, stepColor, trackPieces, valueAt } from './geoTrack'
import { LAYER_DONE, LAYER_OUTSIDE } from './geoLayers'
import type { LegendRect } from './MapLegend.vue'
import MapLegend from './MapLegend.vue'
import TrainArrow from './TrainArrow.vue'

/**
 * Карта диаграммы «Текущие рейсы»: бледно-серые линии маршрутов, ход рейсов с начала периода до момента minute
 * (цвет участка — отклонение от расписания, рейсы накладываются), точки поездов, вагонов и проводников на этот момент.
 * В плашке (MapLegend) — показанные рейсы; она отходит от их маршрутов. Рейсы, законченные к моменту, видны при слое LAYER_DONE.
 * Рейс под мышкой (линия, стрелка или строка списка) выделен: остальные исчезают с карты
 * и бледнеют в списке, если показано больше одного.
 * Поезд рейса в пути — стрелка по ходу в кружке; законченного рейса, если он не скрыт, — кружок там, где рейс закончился. Вагоны и проводники видны только вне рейсов — в последней своей точке.
 *
 * Размер карты задаёт владелец. Маршруты с полями вокруг вписываются в карту, пока пользователь не сменил масштаб или место.
 * interactive = false — неподвижная карта ячейки дашборда: подсказки и выделение рейса есть, списка рейсов нет.
 */
const props = defineProps<{
  chart: GeoChart
  /** момент: минуты от начала периода */
  minute: number
  /** включённые отключаемые слои — LAYER_* */
  layers: number[]
  interactive: boolean
}>()

const mapEl = shallowRef<HTMLElement | null>(null)
const legend = useTemplateRef<InstanceType<typeof MapLegend>>('legend')

/** Минута периода по времени ISO. */
function minuteOf(iso: string): number {
  return (new Date(iso).getTime() - new Date(props.chart.from).getTime()) / 60000
}

/** Рейс закончен к моменту. */
function isDone(trip: GeoTrip): boolean {
  return trip.arrivedDt !== null && minuteOf(trip.arrivedDt) < props.minute
}

/**
 * Рейсы периода по отправлению и их состояние на момент. value — километр и отклонение от расписания (у законченного —
 * на прибытии), null — координат нет.
 */
const trips = computed(() => {
  const routes = new Map(props.chart.routes.map((r) => [r.id, r]))
  return props.chart.trips.flatMap((trip) => {
    const route = routes.get(trip.route)
    if (!route) return []
    const state: 'active' | 'done' | 'future' =
      minuteOf(trip.departedDt) > props.minute ? 'future' : isDone(trip) ? 'done' : 'active'
    const value = valueAt(trip.track, props.minute)
    return [{ trip, route, state, value, bearing: value ? routeBearing(route, value[0]) : 0 }]
  })
})
const activeTrips = computed(() => trips.value.filter((a) => a.state === 'active'))
/** Рейсы списка: все рейсы периода, если показаны законченные, иначе рейсы в пути. */
const listTrips = computed(() => (props.layers.includes(LAYER_DONE) ? trips.value : activeTrips.value))
/** Рейсы на карте: будущие не рисуются. */
const shownTrips = computed(() => listTrips.value.filter((a) => a.state !== 'future'))
const bearings = computed(() => new Map(activeTrips.value.map((a) => [a.trip.id, a.bearing])))

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
let frame = 0
let legendFrame = 0

const routeLines: Polyline[] = []

/** Нарисованная часть хода рейса; line — null, пока момент не дошёл до неё. */
interface DrawnPiece {
  route: GeoRoute
  trip: GeoTrip
  piece: TrackPiece
  line: Polyline | null
  /** нарисована целиком */
  whole: boolean
  /** рейс в пути */
  active: boolean
}
let pieces: DrawnPiece[] = []
const markers = new Map<string, CircleMarker | Marker>()
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
  for (const p of pieces) p.line?.remove()
  pieces = []
  for (const marker of markers.values()) marker.remove()
  markers.clear()
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
    for (const piece of trackPieces(trip)) pieces.push({ route, trip, piece, line: null, whole: false, active: false })
  }
  bounds = all.isValid() ? all : null
  fit()
  update()
}

/** Вписать маршруты; у карты без размера (страница не разложена) масштаб не считается — впишет ResizeObserver. */
function fit() {
  const m = leaflet
  if (!m || touched || !bounds) return
  const size = m.getSize()
  if (size.x === 0 || size.y === 0) return
  const padding = cssPx('--geo-fit-padding')
  m.fitBounds(bounds, { padding: [padding, padding], animate: false })
}

function update() {
  if (!leaflet) return
  drawPieces()
  drawMarkers()
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
  const done = new Set(props.chart.trips.filter(isDone))
  for (const p of pieces) {
    const { piece } = p
    if (isPale(p.trip.id) || piece.from >= props.minute || (!props.layers.includes(LAYER_DONE) && done.has(p.trip))) {
      p.line?.remove()
      p.line = null
      p.whole = false
      continue
    }
    const active = !done.has(p.trip)
    if (p.line && p.active !== active) {
      // рейсы в пути — поверх законченных
      if (active) p.line.bringToFront()
      else moved = true
    }
    p.active = active
    const whole = piece.to <= props.minute
    if (p.line && p.whole && whole) continue
    const toKm = whole ? piece.toKm : (valueAt(p.trip.track, props.minute)?.[0] ?? piece.fromKm)
    const path = routePart(p.route, piece.fromKm, toKm)
    if (p.line) {
      p.line.setLatLngs(path)
    } else {
      p.line = polyline(path, {
        renderer: lineRenderer!,
        bubblingMouseEvents: true,
        color: stepColor(piece.step),
        opacity: 1,
        weight: cssPx('--geo-trip-width'),
        // соседние части не накладываются концами: иначе на стыках видны тёмные точки
        lineCap: 'butt',
      })
        .on('mouseover', () => (hovered.value = p.trip.id))
        .on('mouseout', () => unhover(p.trip.id))
        .addTo(m)
      if (!active) moved = true
    }
    p.whole = whole
  }
  if (moved) for (const p of pieces) if (p.active) p.line?.bringToFront()
}

function drawMarkers() {
  const used = new Set<string>()
  drawTrains(used)
  if (props.layers.includes(LAYER_OUTSIDE)) {
    props.chart.wagons.forEach((w, i) => {
      const place = placeAt(w, props.minute)
      if (!place) return
      show(used, 'wagon', i, place.at, () => hint(t('Вагон {number}', { number: w.wagon.number }), place.last))
    })
    props.chart.usrs.forEach((u, i) => {
      const place = placeAt(u, props.minute)
      if (place) show(used, 'usr', i, place.at, () => hint(u.usr.name, place.last))
    })
  }
  for (const [key, marker] of markers) {
    if (used.has(key)) continue
    marker.remove()
    markers.delete(key)
    if (key.startsWith('train:')) trainIcons.delete(Number(key.slice(6)))
  }
}

/** Подсказка вагона и проводника: имя и время последней координаты. */
function hint(name: string, last: number): string {
  const time = new Date(new Date(props.chart.from).getTime() + last * 60000).toISOString()
  return `${name}\n${t('координата на {time}', { time: formatDateTime(time) })}`
}

function drawTrains(used: Set<string>) {
  for (const { trip, route, value } of shownTrips.value) {
    const place = value && routePlace(route, value[0])
    if (!value || !place || isPale(trip.id)) continue
    show(
      used,
      'train',
      trip.id,
      place,
      () => `${route.trainNumber} ${route.name}\n${trip.train.name}\n${delayText(value[1])}`,
    )
    const icon = markers.get(`train:${trip.id}`)
    if (icon && 'setZIndexOffset' in icon) {
      // рейсы в пути — поверх законченных
      icon.setZIndexOffset(isDone(trip) ? 0 : 10000)
    }
  }
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
  legend.value?.check()
}

/** Проверка плашки не чаще кадра экрана: карту двигают быстрее. */
function scheduleLegend() {
  if (legendFrame) return
  legendFrame = requestAnimationFrame(() => {
    legendFrame = 0
    checkLegend()
  })
}

type MarkerKind = 'train' | 'wagon' | 'usr'

/** Поставить точку: новую или уже стоящую — на новое место. */
function show(used: Set<string>, kind: MarkerKind, id: number, place: [number, number], text: () => string) {
  const key = `${kind}:${id}`
  used.add(key)
  hints.set(key, text)
  const shown = markers.get(key)
  if (shown) {
    shown.setLatLng(place)
    return
  }
  const marker = kind === 'train' ? trainMarker(id, place) : dot(kind, place)
  markers.set(key, marker)
  marker.bindTooltip(
    () => {
      const el = document.createElement('div')
      // первая строка — название, остальные — сведения
      for (const [i, line] of (hints.get(key)?.() ?? '').split('\n').entries()) {
        const div = document.createElement('div')
        div.className = i === 0 ? 'geo-map__hint-name' : 'geo-map__hint-line'
        div.textContent = line
        el.appendChild(div)
      }
      return el
    },
    { direction: 'auto', className: 'geo-map__hint' },
  )
}

function dot(kind: MarkerKind, place: [number, number]): CircleMarker {
  return circleMarker(place, {
    pane: `geo-${kind}`,
    className: `geo-map__${kind}`,
    radius: cssPx(`--geo-${kind}-size`) / 2,
  }).addTo(leaflet!)
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
    .addTo(leaflet!)
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
  // точки — выше линий; меньшие — выше больших: совпавшие видны все
  ;['train', 'wagon', 'usr'].forEach((kind, i) => {
    m.createPane(`geo-${kind}`).style.zIndex = String(450 + i)
  })
  lineRenderer = canvas({ padding: 0.3 })
  // move — во время движения карты, не только после него
  m.on('move moveend zoomend resize', scheduleLegend)
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
  markers.clear()
  trainIcons.clear()
  pieces = []
})

// набор маршрутов сменился — плашка проверяется после своей перерисовки
watch(() => legendRoutes.value.map((r) => r.id).join(), checkLegend, { flush: 'post' })
watch(() => props.chart, rebuild)
watch([() => props.minute, () => props.layers, focused], scheduleUpdate)
</script>

<style scoped lang="scss">
.geo-map {
  --geo-route: #b8b8b8;
  --geo-route-width: 0.3rem;
  --geo-trip-width: 0.3rem;
  --geo-train-size: 1.6rem;
  --geo-wagon-size: 1rem;
  --geo-usr-size: 0.5rem;
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

.geo-map :deep(.geo-map__wagon) {
  fill: #12967c;
  fill-opacity: 1;
  stroke: #fff;
  stroke-width: 0.1rem;
}

.geo-map :deep(.geo-map__usr) {
  fill: #ffc53d;
  fill-opacity: 1;
  stroke: none;
}

// подсказка — как на карте маршрута (RouteMap): плашка без стрелки, строки не переносятся
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
  white-space: nowrap;

  &::before {
    display: none;
  }
}

.geo-map :deep(.geo-map__hint-name) {
  font-weight: $zs-route-stop-weight;
}

.geo-map :deep(.geo-map__hint-line) {
  font-size: 0.8rem;
}
</style>
