<template>
  <div class="marks-map">
    <div ref="mapEl" class="marks-map__map" />
    <!-- своя загрузка точек: спиннер и ошибка — поверх карты -->
    <div v-if="source?.loading" class="marks-map__state q-pa-sm">
      <LoadSpinner />
    </div>
    <div v-else-if="source?.error" class="marks-map__state q-pa-sm">
      <ErrorBox :error="source.error" :retry="source.retry" />
    </div>
  </div>
</template>

<script setup lang="ts">
import 'leaflet/dist/leaflet.css'

import type { LatLngBounds, LayerGroup, Map as LeafletMap } from 'leaflet'
import { latLngBounds, layerGroup, map as createMap, polyline, tileLayer } from 'leaflet'
import { onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'

import type { MapPoint } from '@/api/stages'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import type { Loadable } from '@/shared/loadable'
import type { MarkKind, PointMark } from './mapMarks'
import { DEFAULT_CENTER, DEFAULT_ZOOM, focusMap, markCircle, POINT_ZOOM, thinnedMarks } from './mapMarks'

/**
 * Точки с подписями на карте OSM, прореженные под масштаб (mapMarks.ts). Выделенные (strong) — крупнее, с полужирной
 * подписью; при прореживании они важнее остальных. Выбранные (selected) важнее всех; карта их показывает (showSelected).
 * Под точками — линия (line).
 *
 * Щелчок по точке или её подписи — событие pick, двойной щелчок — open (первый щелчок перед ним — pick). Масштаб и место
 * карты меняют пользователь и владелец (fit). Размер карты задаёт владелец.
 */
const props = withDefaults(
  defineProps<{
    /** точки по порядку важности */
    marks: PointMark[]
    /** id выделенных точек */
    strong?: ReadonlySet<number>
    /** id выбранных точек */
    selected?: readonly number[]
    /** линия под точками; null — нет */
    line?: MapPoint[] | null
    /** id точек, у которых прореживается только подпись */
    keep?: ReadonlySet<number>
    /** загрузка marks */
    source?: Loadable | null
  }>(),
  { strong: () => new Set<number>(), selected: () => [], line: null, keep: () => new Set<number>(), source: null },
)

const emit = defineEmits<{ pick: [mark: PointMark]; open: [mark: PointMark] }>()

const mapEl = shallowRef<HTMLElement | null>(null)
let leaflet: LeafletMap | null = null
let layer: LayerGroup | null = null
let resizeObserver: ResizeObserver | null = null
// вписать, когда у карты появится размер: без него Leaflet не считает масштаб
let pending: LatLngBounds | null = null
// несколько выбранных точек занимают меньшую часть карты — слишком мелко; крупнее PAIR_ZOOM карта их не показывает
const SMALL_PART = 0.05
const PAIR_ZOOM = 13
let pendingZoom = POINT_ZOOM

// последний щелчок. Двойной щелчок считается по щелчкам, а не событием dblclick: после первого владелец может
// перерисовать точки, и второй придётся на новый кружок
const DOUBLE_CLICK = 500
let lastId: number | null = null
let lastTime = 0

function onClick(mark: PointMark) {
  const time = performance.now()
  const double = mark.id === lastId && time - lastTime <= DOUBLE_CLICK
  lastId = double ? null : mark.id
  lastTime = time
  if (double) emit('open', mark)
  else emit('pick', mark)
}

function draw() {
  const m = leaflet
  if (!m || !layer) return
  layer.clearLayers()
  if (props.line) {
    polyline(
      props.line.map((p): [number, number] => [p.y, p.x]),
      { className: 'map-line', interactive: false },
    ).addTo(layer)
  }
  const kind = (mark: PointMark): MarkKind =>
    props.selected.includes(mark.id) ? 'selected' : props.strong.has(mark.id) ? 'strong' : 'normal'
  // выбранные видны всегда; выделенные — когда их не больше пары
  const must = new Set([...props.selected, ...(props.strong.size <= 2 ? props.strong : [])])
  const order: MarkKind[] = ['selected', 'strong', 'normal']
  const marks = order.flatMap((k) => props.marks.filter((mark) => kind(mark) === k))
  // те, что видны всегда, — с востока на запад: у восточной подпись справа, западной рядом с ней — слева, а не друг на друге
  const rank = (mark: PointMark) => (must.has(mark.id) ? 0 : props.keep.has(mark.id) ? 1 : 2)
  marks.sort((a, b) => rank(a) - rank(b) || (rank(a) === 0 ? b.point.x - a.point.x : 0))
  // важные рисуются последними — поверх остальных
  const shownMarks = thinnedMarks(m, marks, must, props.keep)
  shownMarks.sort((a, b) => order.indexOf(kind(b.mark)) - order.indexOf(kind(a.mark)))
  for (const shown of shownMarks) {
    markCircle(shown, kind(shown.mark), () => onClick(shown.mark)).addTo(layer)
  }
}

function applyFit() {
  const m = leaflet
  if (!m || !pending) return
  const size = m.getSize()
  if (size.x === 0 || size.y === 0) return
  const bounds = pending
  pending = null
  m.fitBounds(bounds.pad(0.1), { maxZoom: pendingZoom, animate: false })
}

/** Показать все эти точки; точек нет — карта остаётся на месте. */
function fit(marks: PointMark[], maxZoom = POINT_ZOOM) {
  if (marks.length === 0) return
  pendingZoom = maxZoom
  pending = latLngBounds(marks.map((mark) => [mark.point.y, mark.point.x]))
  applyFit()
}

/** Приблизить карту к точке (focusMap). */
function focus(mark: PointMark) {
  if (leaflet) focusMap(leaflet, mark.point)
}

defineExpose({ fit, show, focus })

onMounted(() => {
  if (!mapEl.value) return
  const m = createMap(mapEl.value, { center: DEFAULT_CENTER, zoom: DEFAULT_ZOOM, doubleClickZoom: false })
  leaflet = m
  m.attributionControl.setPrefix(false)
  tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  }).addTo(m)
  layer = layerGroup().addTo(m)
  m.on('moveend', draw)
  draw()
  applyFit()
  resizeObserver = new ResizeObserver(() => {
    m.invalidateSize()
    applyFit()
  })
  resizeObserver.observe(mapEl.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  leaflet?.remove()
  leaflet = null
  layer = null
})

/**
 * Показать точки (и линию между ними), меняя карту как можно меньше: за краем — карта сдвигается, не помещаются —
 * уменьшается, слишком мелко — увеличивается до SMALL_PART карты. Точка одна — масштаб прежний.
 */
function show(marks: PointMark[], line: MapPoint[] = []) {
  const m = leaflet
  if (!m || marks.length === 0) return
  const size = m.getSize()
  // у карты ещё нет размера — вписать, когда появится
  if (size.x === 0 || size.y === 0) {
    fit(marks)
    return
  }
  const bounds = latLngBounds([...marks.map((mark) => mark.point), ...line].map((p) => [p.y, p.x]))
  const from = m.latLngToContainerPoint(bounds.getSouthWest())
  const to = m.latLngToContainerPoint(bounds.getNorthEast())
  const part = Math.max(Math.abs(to.x - from.x) / size.x, Math.abs(to.y - from.y) / size.y)
  const zoom = m.getZoom()
  const target =
    part === 0 || part >= SMALL_PART
      ? zoom
      : Math.max(zoom, Math.min(PAIR_ZOOM, zoom + Math.ceil(Math.log2(SMALL_PART / part))))
  if (m.getBounds().contains(bounds) && target === zoom) return
  pending = bounds
  pendingZoom = target
  applyFit()
}

/** Показать выбранные точки; у нескольких — и линию между ними. */
function showSelected() {
  const points = props.marks.filter((mark) => props.selected.includes(mark.id))
  show(points, points.length > 1 ? (props.line ?? []) : [])
}

watch(
  () => props.selected.join(','),
  () => {
    draw()
    showSelected()
  },
)
watch([() => props.marks, () => props.strong, () => props.line, () => props.keep], draw)
// линия загружается позже выбора и может выходить за край
watch(
  () => props.line,
  (line) => {
    if (line) showSelected()
  },
)
</script>

<style scoped lang="scss">
.marks-map {
  position: relative;
  // свой слой: кнопки карты (z-index Leaflet) не выходят поверх списков и меню
  z-index: 0;
  min-height: 10em;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  overflow: hidden;
  background: $zs-bg-header;
}

.marks-map__map {
  position: absolute;
  inset: 0;
  font-family: $zs-font;
}

.marks-map__state {
  position: absolute;
  right: 0;
  bottom: 1.5rem;
  z-index: 1000;
  max-width: 100%;
}
</style>
