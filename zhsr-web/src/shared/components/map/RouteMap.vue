<template>
  <div class="route-map">
    <div ref="mapEl" class="route-map__map" />
    <!-- своя загрузка компонента: в ожидание фрейма не входит, спиннер и ошибка — поверх карты -->
    <div v-if="load.loading" class="route-map__overlay q-pa-md">
      <LoadSpinner />
    </div>
    <div v-else-if="load.error" class="route-map__overlay q-pa-md">
      <ErrorBox :error="load.error" :retry="load.retry" class="route-map__error" />
    </div>
  </div>
</template>

<script setup lang="ts">
import 'leaflet/dist/leaflet.css'

import type {
  CircleMarker,
  LatLngBounds,
  LatLngExpression,
  LayerGroup,
  LeafletMouseEvent,
  Map as LeafletMap,
} from 'leaflet'
import { circleMarker, latLngBounds, layerGroup, map as createMap, polyline, tileLayer } from 'leaflet'
import { nextTick, onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'

import type { RoutePoint } from '@/api/routes'
import type { MapPoint, StageLine, StageLines } from '@/api/stages'
import { fetchStageLines } from '@/api/stages'
import { t } from '@/boot/i18n'
import { formatHm } from '@/desktop/routes/duration'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { useLoad } from '@/shared/loadable'

/**
 * Маршрут на карте OSM: линии перегонов между соседними станциями маршрута, станции кружками с названиями. Карта
 * масштабируется колесом мыши и кнопками «+» и «−». Названия остановок видны всегда, станций следования — при
 * наведении; у выбранной станции (selected) — всегда.
 *
 * Станции следования: показ включён (showPassing) — видны всегда; выключен — только когда карта увеличена не меньше
 * passingZoom (мельче они сливаются в сплошную линию). Выбранная станция видна всегда.
 *
 * Размер карты задаёт владелец (ширина и высота корня компонента); высота — не меньше 10em. Маршрут вписывается в
 * карту при первом рисунке и при смене showPassing (развернули или свернули станции следования); смена станций
 * (правка в редакторе) и размера масштаб и место карты не меняют.
 *
 * Перегоны и точки станций компонент грузит сам (GET /stages/lines) — это независимая загрузка: спиннер и ошибка —
 * на месте карты, в ожидание фрейма она не входит.
 *
 * События:
 * - station — щелчок по станции: станция, её точка маршрута и её номер в points, точка станции на карте;
 * - stage — щелчок по линии перегона: перегон, точки маршрута, между которыми он лежит, и ближайшая к месту щелчка
 *   точка линии.
 */
const props = withDefaults(
  defineProps<{
    /** все станции маршрута по порядку: остановки и станции следования (Route.points) */
    points: RoutePoint[]
    /** выбранная станция — id справочника Station; null — не выбрана */
    selected?: number | null
    /** показывать станции следования при любом масштабе; false — только с масштаба passingZoom */
    showPassing?: boolean
    /** масштаб карты (уровень Leaflet), с которого видны станции следования, когда showPassing выключен */
    passingZoom?: number
  }>(),
  { selected: null, showPassing: true, passingZoom: 8 },
)

/** Щелчок по станции. */
export interface RouteMapStationClick {
  /** станция — id справочника Station */
  station: number
  /** номер точки маршрута в points; станция встречается в маршруте несколько раз — первая */
  index: number
  /** точка маршрута станции */
  point: RoutePoint
  /** точка станции на карте */
  mapPoint: MapPoint
}

/** Щелчок по линии перегона. */
export interface RouteMapStageClick {
  /** перегон: id, станции и точки линии */
  line: StageLine
  /** номер в points точки маршрута, от которой идёт перегон; следующая точка маршрута — index + 1 */
  index: number
  /** точка маршрута, от которой идёт перегон */
  from: RoutePoint
  /** точка маршрута, к которой идёт перегон */
  to: RoutePoint
  /** точка линии, ближайшая к месту щелчка */
  node: MapPoint
}

const emit = defineEmits<{
  station: [click: RouteMapStationClick]
  stage: [click: RouteMapStageClick]
}>()

// Оформление — как у схемы маршрута (RouteStops), кроме линии пути и станции следования (размер, цвет кольца):
// общие переменные $zs-route-* в quasar.variables.scss. Цвет и толщина линий и кружков — в стилях компонента по
// классам SVG-элементов (className). Радиус кружка Leaflet берёт только числом в пикселях: он считается из размеров
// точек в rem — CSS-переменных корня компонента (.route-map).

// вся республика — пока рисовать нечего
const DEFAULT_CENTER: LatLngExpression = [48.0, 67.0]
const DEFAULT_ZOOM = 5

const mapEl = shallowRef<HTMLElement | null>(null)
const data = shallowRef<StageLines | null>(null)

let leaflet: LeafletMap | null = null
let drawing: LayerGroup | null = null
// границы маршрута: карта вписывает их после загрузки и при смене размера
let routeBounds: LatLngBounds | null = null
let resizeObserver: ResizeObserver | null = null
// маршрут уже вписан в карту: дальше масштаб и место карты меняет только пользователь
let fitted = false
// кружки станций по id станции: при смене выбранной станции меняется только их оформление
const markers = new Map<number, CircleMarker>()
// станции следования — их кружки снимаются с карты и возвращаются по масштабу (updatePassing)
const passing = new Set<number>()

/** Станции маршрута одной строкой: по ней видно, что станции сменились и рисунок пути надо загрузить заново. */
function stationsKey(): string {
  return props.points.map((p) => p.station).join(',')
}

const load = useLoad(async () => {
  // станции сменились, пока шла загрузка (правка в редакторе), — загрузка повторяется по новым станциям: показывается
  // только рисунок пути по тем станциям, что сейчас в points
  let key: string
  let lines: StageLines
  do {
    key = stationsKey()
    // названия станций для подписей — справочник Station, параллельно с перегонами
    ;[lines] = await Promise.all([
      fetchStageLines(props.points.map((p) => p.station)),
      dictManager.prepareDict('Station'),
    ])
  } while (key !== stationsKey())
  data.value = lines
})

function latLng(p: MapPoint): LatLngExpression {
  return [p.y, p.x]
}

/**
 * Остановка маршрута: начальная и конечная станции — тоже остановки. Станции уже нет в points (правка в редакторе,
 * рисунок пути ещё грузится заново) — не остановка.
 */
function isStop(index: number): boolean {
  const point = props.points[index]
  if (!point) return false
  return point.haveStop || index === 0 || index === props.points.length - 1
}

/** Номер станции в points: первая точка маршрута с этой станцией; остановка важнее станции следования. */
function indexOf(station: number): number {
  const stop = props.points.findIndex((p, i) => p.station === station && isStop(i))
  return stop !== -1 ? stop : props.points.findIndex((p) => p.station === station)
}

/** Размер из CSS-переменной корня компонента (в rem), пикселей экрана. */
function cssPx(name: string): number {
  const el = mapEl.value
  if (!el) return 0
  const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
  const px = parseFloat(getComputedStyle(el).getPropertyValue(name)) * rem
  return Number.isFinite(px) ? px : 0
}

/**
 * Радиус кружка станции, пикселей. Обводка SVG лежит по обе стороны контура, поэтому у кольца станции следования
 * контур — на половину толщины кольца внутрь: внешний размер совпадает с размером точки на схеме маршрута.
 */
function radius(station: number, stop: boolean): number {
  if (station === props.selected) return cssPx('--route-selected-size') / 2
  if (stop) return cssPx('--route-stop-size') / 2
  return (cssPx('--route-pass-size') - cssPx('--route-pass-ring')) / 2
}

/** Отступ подписи от центра кружка: радиус и промежуток между точкой и названием, как на схеме маршрута. */
function labelOffset(station: number, stop: boolean): number {
  return radius(station, stop) + cssPx('--route-name-gap')
}

/** Ближайшая к месту щелчка точка линии; долгота сжата по широте, чтобы расстояние было похоже на настоящее. */
function nearest(points: MapPoint[], lat: number, lng: number): MapPoint {
  const k = Math.cos((lat * Math.PI) / 180)
  let best = points[0]!
  let bestD = Infinity
  for (const p of points) {
    const d = ((p.x - lng) * k) ** 2 + (p.y - lat) ** 2
    if (d < bestD) {
      bestD = d
      best = p
    }
  }
  return best
}

function draw() {
  const m = leaflet
  if (!m) return
  drawing?.remove()
  markers.clear()
  passing.clear()
  routeBounds = null
  const group = layerGroup().addTo(m)
  drawing = group
  const d = data.value
  if (!d) return

  const bounds = latLngBounds([])

  for (const line of d.lines) {
    const index = line.index
    if (line.points.length < 2 || !props.points[index + 1]) continue
    const path = line.points.map(latLng)
    polyline(path, { className: 'route-map__line', interactive: false }).addTo(group)
    // невидимая широкая линия поверх видимой: по тонкой линии трудно попасть щелчком
    polyline(path, { className: 'route-map__hit' })
      .on('click', (e: LeafletMouseEvent) => {
        emit('stage', {
          line,
          index,
          from: props.points[index]!,
          to: props.points[index + 1]!,
          node: nearest(line.points, e.latlng.lat, e.latlng.lng),
        })
      })
      .addTo(group)
    for (const p of path) bounds.extend(p)
  }

  // станции следования — снизу, остановки — поверх них
  const stations = d.stations
    .map((s) => ({ ...s, index: indexOf(s.station) }))
    .filter((s) => s.index !== -1)
    .sort((a, b) => Number(isStop(a.index)) - Number(isStop(b.index)))
  for (const s of stations) {
    const stop = isStop(s.index)
    const marker = circleMarker(latLng(s.point), {
      className: stop ? 'route-map__stop' : 'route-map__pass',
      radius: radius(s.station, stop),
      bubblingMouseEvents: false,
    })
    marker.on('click', () => {
      emit('station', { station: s.station, index: s.index, point: props.points[s.index]!, mapPoint: s.point })
    })
    // подсказка при наведении: у подписанной станции подпись на это время сменяется подсказкой
    marker.on('mouseover', () => showHint(marker, s.station, true))
    marker.on('mouseout', () => showHint(marker, s.station, false))
    // кружок станции следования снова кладётся на карту — SVG-элемент новый, отметка выбранной ставится заново
    marker.on('add', () => {
      marker.getElement()?.classList.toggle('route-map__station--selected', s.station === props.selected)
    })
    marker.addTo(group)
    markers.set(s.station, marker)
    if (!stop) passing.add(s.station)
    bounds.extend(latLng(s.point))
  }

  routeBounds = bounds.isValid() ? bounds : null
  // при первом рисунке маршрут вписывается; правка станций (редактор) масштаб и место карты не меняет
  if (!fitted) fit()
  applySelected()
}

/**
 * Вписать маршрут в карту; маршрута нет — вся республика. У карты нулевого размера (ещё не разложена страница) масштаб
 * не считается: Leaflet получил бы NaN и перестал рисовать — карта впишется, когда появится размер (ResizeObserver).
 */
function fit() {
  const m = leaflet
  if (!m) return
  const size = m.getSize()
  if (size.x === 0 || size.y === 0) return
  if (routeBounds) {
    fitted = true
    const padding = cssPx('--route-fit-padding')
    m.fitBounds(routeBounds, { padding: [padding, padding] })
  } else {
    m.setView(DEFAULT_CENTER, DEFAULT_ZOOM)
  }
}

/** Станции следования на карте: показ включён или масштаб не меньше passingZoom; выбранная станция — всегда. */
function updatePassing() {
  const m = leaflet
  const group = drawing
  if (!m || !group) return
  const shown = props.showPassing || m.getZoom() >= props.passingZoom
  for (const station of passing) {
    const marker = markers.get(station)
    if (!marker) continue
    const visible = shown || station === props.selected
    if (visible && !group.hasLayer(marker)) group.addLayer(marker)
    if (!visible && group.hasLayer(marker)) group.removeLayer(marker)
  }
}

/** Подписана ли станция всегда: остановка и выбранная станция; станция следования — только подсказкой. */
function isLabeled(station: number, stop: boolean): boolean {
  return stop || station === props.selected
}

/**
 * Подсказка станции — то же, что о станции на схеме маршрута (RouteStops), по строке: название, расстояние от начала
 * маршрута («отправление» у начальной), время в пути от предыдущей станции, стоянка.
 */
function hintContent(station: number): HTMLElement {
  const index = indexOf(station)
  const point = props.points[index]
  const lines: { text: string; cls: string }[] = [
    { text: dictManager.getValue('Station', station), cls: isStop(index) ? 'route-map__hint-name--stop' : '' },
  ]
  // станции уже нет в points (правка в редакторе, рисунок пути ещё грузится заново) — только название
  if (point) lines.push({ text: index === 0 ? t('отправление') : t('{km} км', { km: point.km }), cls: '' })
  if (point && point.runDuration > 0)
    lines.push({ text: t('в пути {time}', { time: formatHm(point.runDuration) }), cls: '' })
  if (point && point.stopDuration > 0) lines.push({ text: t('стоянка {n} мин', { n: point.stopDuration }), cls: '' })
  const el = document.createElement('div')
  lines.forEach((line, i) => {
    const div = document.createElement('div')
    div.className = i === 0 ? `route-map__hint-name ${line.cls}` : 'route-map__hint-line'
    div.textContent = line.text
    el.appendChild(div)
  })
  return el
}

/**
 * Подпись станции. Остановки и выбранная станция подписаны всегда — названием; станции следования — только
 * подсказкой при наведении.
 */
function bindLabel(marker: CircleMarker, station: number, stop: boolean) {
  const labeled = isLabeled(station, stop)
  const classes = ['route-map__label']
  if (stop) classes.push('route-map__label--stop')
  if (station === props.selected) classes.push('route-map__label--selected')
  if (!labeled) classes.push('route-map__label--hint')
  marker.unbindTooltip()
  marker.bindTooltip(labeled ? dictManager.getValue('Station', station) : hintContent(station), {
    permanent: labeled,
    direction: 'right',
    offset: [labelOffset(station, stop), 0],
    className: classes.join(' '),
  })
}

/** Наведение на подписанную станцию: подпись сменяется подсказкой и обратно. */
function showHint(marker: CircleMarker, station: number, on: boolean) {
  const stop = isStop(indexOf(station))
  if (!isLabeled(station, stop)) return
  marker.setTooltipContent(on ? hintContent(station) : dictManager.getValue('Station', station))
  marker.getTooltip()?.getElement()?.classList.toggle('route-map__label--hint', on)
}

/** Оформление кружков и подписей по выбранной станции; выбранная — поверх остальных. */
function applySelected() {
  for (const [station, marker] of markers) {
    const stop = isStop(indexOf(station))
    const selected = station === props.selected
    marker.setRadius(radius(station, stop))
    marker.getElement()?.classList.toggle('route-map__station--selected', selected)
    bindLabel(marker, station, stop)
    if (selected) marker.bringToFront()
  }
  updatePassing()
}

onMounted(() => {
  if (!mapEl.value) return
  const m = createMap(mapEl.value, { center: DEFAULT_CENTER, zoom: DEFAULT_ZOOM })
  leaflet = m
  // без значка и ссылки Leaflet: на маленькой карте подпись занимает много места; ссылка на OSM обязательна
  m.attributionControl.setPrefix(false)
  m.on('zoomend', updatePassing)
  tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  }).addTo(m)
  draw()
  // размер карты сменил владелец (или окно) — Leaflet пересчитывает размер, середина карты остаётся на месте;
  // маршрут ещё не вписан (у карты не было размера) — вписывается
  resizeObserver = new ResizeObserver(() => {
    m.invalidateSize()
    if (!fitted) fit()
  })
  resizeObserver.observe(mapEl.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  leaflet?.remove()
  leaflet = null
  drawing = null
  routeBounds = null
  markers.clear()
  passing.clear()
})

watch(data, draw)
watch(() => props.selected, applySelected)
watch(() => props.passingZoom, updatePassing)
// станции следования развернули или свернули — маршрут вписывается заново: владелец вместе с этим обычно меняет и
// размер карты, поэтому сначала — новый размер (после отрисовки владельца)
watch(
  () => props.showPassing,
  async () => {
    await nextTick()
    leaflet?.invalidateSize()
    fit()
    updatePassing()
  },
)
// станции маршрута сменились — рисунок пути грузится заново
watch(stationsKey, () => void load.run())
// станции те же, сменились остановки (удалили остановку в редакторе — она стала станцией следования) — рисунок пути
// тот же, кружки и подписи рисуются заново
watch(
  () => props.points.map((p) => (p.haveStop ? 's' : 'p')).join(''),
  () => draw(),
)

void load.run()
</script>

<style scoped lang="scss">
.route-map {
  position: relative;
  min-height: 10em;
  overflow: hidden;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: $zs-bg-header;
}

// карта — на весь корень: у корня может быть только наименьшая высота, от неё height: 100% не считается
.route-map__map {
  position: absolute;
  inset: 0;
  font-family: $zs-font;
}

.route-map__overlay {
  position: absolute;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(255 255 255 / 70%);
}

.route-map__error {
  max-width: 100%;
}

// кнопки масштаба Leaflet («+» и «−») — полупрозрачные, чтобы не закрывали карту; под курсором — непрозрачные
.route-map :deep(.leaflet-control-zoom) {
  opacity: 0.6;
  transition: opacity 0.15s;

  &:hover {
    opacity: 1;
  }
}

// --- рисунок пути: оформление схемы маршрута (RouteStops), переменные $zs-route-* ---

// выбранная станция есть только на карте: точка крупнее остановки, цвет акцента
$route-selected-size: 0.9rem;
$route-selected: $primary;
// линия пути есть и на схеме маршрута, но на карте она своя
$route-line: $primary;
$route-line-width: 0.2rem;
// станция следования на карте мельче, чем на схеме маршрута: на линии пути их много
$route-pass-size: 0.4rem;

// размеры точек для радиусов кружков (cssPx): Leaflet берёт радиус числом в пикселях
.route-map {
  --route-stop-size: #{$zs-route-stop-size};
  --route-pass-size: #{$route-pass-size};
  --route-pass-ring: #{$zs-route-pass-ring};
  --route-selected-size: #{$route-selected-size};
  // промежуток между точкой и названием — как в строке схемы маршрута
  --route-name-gap: 0.4rem;
  // поля вокруг маршрута, когда карта вписывает его
  --route-fit-padding: 0.8rem;
}

// линии и кружки — SVG-элементы Leaflet: стили важнее атрибутов, которые ставит Leaflet
// линия пути на карте — синяя и толще, чем на схеме маршрута: серая тонкая линия теряется на подложке
.route-map :deep(.route-map__line) {
  fill: none;
  stroke: $route-line;
  stroke-opacity: 0.85;
  stroke-width: $route-line-width;
}

.route-map :deep(.route-map__hit) {
  fill: none;
  stroke-opacity: 0;
  stroke-width: 1rem;
}

.route-map :deep(.route-map__stop) {
  fill: $zs-route-dot;
  fill-opacity: 1;
  stroke: none;
}

.route-map :deep(.route-map__pass) {
  fill: $zs-route-pass-bg;
  fill-opacity: 1;
  // кольцо — в цвет линии пути: серое кольцо схемы маршрута на синей линии не видно
  stroke: $route-line;
  stroke-opacity: 1;
  stroke-width: $zs-route-pass-ring;
}

.route-map :deep(.route-map__station--selected) {
  fill: $route-selected;
  fill-opacity: 1;
  stroke: none;
}

// подписи станций — всплывающие подсказки Leaflet: без рамки, стрелки и своего отступа; текст с белой обводкой, чтобы
// читался поверх карты
.route-map :deep(.route-map__label) {
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  box-shadow: none;
  color: $zs-route-pass-name;
  font-size: $zs-route-name-size;
  font-weight: 400;
  white-space: nowrap;
  text-shadow:
    0 0 0.2em #fff,
    0 0 0.2em #fff,
    0 0 0.2em #fff;

  &::before {
    display: none;
  }
}

.route-map :deep(.route-map__label--stop) {
  color: $zs-route-stop-name;
  font-weight: $zs-route-stop-weight;
}

.route-map :deep(.route-map__label--selected) {
  color: $route-selected;
  font-weight: $zs-route-stop-weight;
}

// подсказка при наведении: плашка, строки обычным цветом текста
.route-map :deep(.route-map__label--hint) {
  z-index: 1;
  padding: 0.4rem 0.5rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
  box-shadow: 0 0.1rem 0.5rem rgb(0 0 0 / 15%);
  color: $zs-text;
  font-weight: 400;
  line-height: 1.35;
  text-shadow: none;
}

.route-map :deep(.route-map__hint-name) {
  color: $zs-text;
}

.route-map :deep(.route-map__hint-name--stop) {
  font-weight: $zs-route-stop-weight;
}

.route-map :deep(.route-map__hint-line) {
  color: $zs-text;
  font-size: 0.8rem;
}
</style>
