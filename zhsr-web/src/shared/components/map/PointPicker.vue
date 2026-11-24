<template>
  <div class="point-picker">
    <div ref="mapEl" class="point-picker__map" />
    <!-- своя загрузка: точки рядом ещё грузятся или не загрузились; выбирать точку это не мешает -->
    <div v-if="source?.loading" class="point-picker__state q-pa-sm">
      <LoadSpinner />
    </div>
    <div v-else-if="source?.error" class="point-picker__state q-pa-sm">
      <ErrorBox :error="source.error" :retry="source.retry" />
    </div>
  </div>
</template>

<script setup lang="ts">
import 'leaflet/dist/leaflet.css'

import type { CircleMarker, LatLngExpression, LayerGroup, LeafletMouseEvent, Map as LeafletMap } from 'leaflet'
import { circleMarker, latLngBounds, layerGroup, map as createMap, tileLayer } from 'leaflet'
import { onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'

import type { MapPoint } from '@/api/stages'
import { useDialogEdit } from '@/shared/components/dialog/dialogEdit'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import type { Loadable } from '@/shared/loadable'
import type { PointMark } from './mapMarks'
import {
  DEFAULT_CENTER,
  DEFAULT_ZOOM,
  focusMap,
  markCircle,
  markLabel,
  POINT_ZOOM,
  nearestMarks,
  nearMarks,
  thinnedMarks,
} from './mapMarks'

/**
 * Выбор точки на карте OSM: щелчок ставит точку (координаты — до 6 знаков после запятой). Точку задал владелец — кружок
 * переезжает, карта сдвигается, если точка за её краем. Размер карты задаёт владелец, высота — не меньше 10em.
 *
 * После щелчка карта приближается к точке (focusMap).
 *
 * Точки рядом (marks) — мелкие кружки с подписями, по всей видимой части карты: прореженные под масштаб; рядом
 * с выбранной точкой (nearMarks) кружки — все, подписи прорежены. Сначала карта показывает
 * выбранную точку и FIT_COUNT ближайших к ней.
 */
const props = withDefaults(
  defineProps<{
    /** подпись выбранной точки; пусто — без подписи */
    text?: string
    marks?: PointMark[]
    /** загрузка marks: спиннер и ошибка — поверх карты */
    source?: Loadable | null
  }>(),
  { text: '', marks: () => [], source: null },
)

const model = defineModel<MapPoint | null>({ default: null })
const edited = useDialogEdit()

const FIT_COUNT = 4

const mapEl = shallowRef<HTMLElement | null>(null)
let leaflet: LeafletMap | null = null
let marker: CircleMarker | null = null
let marksLayer: LayerGroup | null = null
// карта уже показала ближайшие точки или её двигал пользователь: дальше масштаб и место меняет только он
let fitted = false
let resizeObserver: ResizeObserver | null = null

function round(value: number): number {
  return Math.round(value * 1e6) / 1e6
}

/** Подпись выбранной точки — полужирная, цвета кружка, поверх остальных. */
function drawText() {
  marker?.unbindTooltip()
  if (!props.text) return
  marker?.bindTooltip(markLabel(props.text), {
    permanent: true,
    direction: 'right',
    opacity: 1,
    className: 'map-label map-label--point map-label--top',
  })
}

function drawMarks() {
  const m = leaflet
  if (!m || !marksLayer) return
  marksLayer.clearLayers()
  const p = model.value
  // сначала — выбранная точка и ближайшие к ней; сдвиг карты (moveend) нарисует точки её видимой части
  const size = m.getSize()
  if (p && !fitted && props.marks.length > 0 && size.x > 0 && size.y > 0) {
    fitted = true
    const bounds = latLngBounds([[p.y, p.x]])
    for (const mark of nearestMarks(props.marks, p).slice(0, FIT_COUNT)) {
      bounds.extend([mark.point.y, mark.point.x])
    }
    m.fitBounds(bounds.pad(0.2), { maxZoom: POINT_ZOOM, animate: false })
    marksLayer.clearLayers()
  }
  // подпись выбранной точки в прореживании не участвует: она поверх всех
  const ordered = p ? nearestMarks(props.marks, p) : props.marks
  const shownMarks = thinnedMarks(m, ordered, new Set(), p ? nearMarks(props.marks, [p]) : new Set())
  for (const shown of shownMarks) markCircle(shown).addTo(marksLayer)
  marker?.bringToFront()
}

function draw() {
  const m = leaflet
  if (!m) return
  const p = model.value
  if (!p) {
    marker?.remove()
    marker = null
    drawMarks()
    return
  }
  const at: LatLngExpression = [p.y, p.x]
  if (marker) marker.setLatLng(at)
  else {
    marker = circleMarker(at, { className: 'map-point', interactive: false }).addTo(m)
    drawText()
  }
  if (!m.getBounds().contains(at)) m.setView(at)
  drawMarks()
}

onMounted(() => {
  if (!mapEl.value) return
  const p = model.value
  const m = createMap(mapEl.value, {
    center: p ? [p.y, p.x] : DEFAULT_CENTER,
    zoom: p ? POINT_ZOOM : DEFAULT_ZOOM,
    // двойной щелчок — это два щелчка: он ставил бы точку и сразу менял масштаб
    doubleClickZoom: false,
  })
  leaflet = m
  m.attributionControl.setPrefix(false)
  tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  }).addTo(m)
  marksLayer = layerGroup().addTo(m)
  m.on('moveend', drawMarks)
  m.on('dragstart', () => {
    fitted = true
  })
  m.on('click', (e: LeafletMouseEvent) => {
    // карту могли прокрутить через край света: долгота приводится к -180…180
    const at = e.latlng.wrap()
    fitted = true
    const p = { x: round(at.lng), y: round(at.lat) }
    model.value = p
    focusMap(m, p)
    edited()
  })
  draw()
  resizeObserver = new ResizeObserver(() => {
    m.invalidateSize()
    if (!fitted) drawMarks()
  })
  resizeObserver.observe(mapEl.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  leaflet?.remove()
  leaflet = null
  marker = null
  marksLayer = null
})

watch(model, draw)
watch(() => props.text, drawText)
watch(() => props.marks, drawMarks)
</script>

<style scoped lang="scss">
.point-picker {
  position: relative;
  // свой слой: кнопки карты (z-index Leaflet) не выходят поверх списков диалога
  z-index: 0;
  min-height: 10em;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  overflow: hidden;
  background: $zs-bg-header;
}

// карта — на весь корень: у корня может быть только наименьшая высота, от неё height: 100% не считается
.point-picker__map {
  position: absolute;
  inset: 0;
  font-family: $zs-font;
  cursor: crosshair;
}

.point-picker__state {
  position: absolute;
  right: 0;
  bottom: 1.5rem;
  z-index: 1000;
  max-width: 100%;
}
</style>
