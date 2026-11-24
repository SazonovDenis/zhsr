<template>
  <MarksMap
    ref="map"
    :marks="marks"
    :strong="strong"
    :selected="selected"
    :line="line.line.value"
    :source="load.loading || load.error ? load : line.load"
    @pick="pick"
    @open="onOpen"
  />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, shallowRef, useTemplateRef, watch } from 'vue'

import type { AdminRow } from '@/api/admin'
import { fetchAdminAll } from '@/api/admin'
import type { PointMark } from '@/shared/components/map/mapMarks'
import MarksMap from '@/shared/components/map/MarksMap.vue'
import { useLoad } from '@/shared/loadable'
import { ADMIN_MAPS, fetchStationMarks, useStationLine } from './adminMaps'

/**
 * Карта списка администрирования (таблицы — ADMIN_MAPS). Станции, депо и парки отстоя — сами записи (депо и парк — в
 * точке своей станции); у перегонов на карте станции.
 *
 * Найденные поиском выделены (у перегона — обе станции), остальные обычные; поиск пустой — выделенных нет. Сменился поиск
 * — карта показывает все найденные, меняя масштаб как можно меньше; сброшен — масштаб и место прежние. Щелчок — название в поиск (событие pick), масштаб и место карты
 * при этом не меняются; повторный щелчок — событие clear: снять выбор и поиск; двойной щелчок по записи — событие open. Выбранная в списке строка (row) выбрана и на карте, у
 * перегона — пара станций и линия между ними. Загрузка своя: список её не ждёт, спиннер и ошибка — на карте.
 */
const props = defineProps<{ table: string; search: string; row: AdminRow | null }>()
const emit = defineEmits<{ pick: [name: string]; clear: []; open: [id: number] }>()

/** Пауза после ввода в поиск перед запросом, мс. */
const SEARCH_DELAY = 300

const def = ADMIN_MAPS[props.table]!

const map = useTemplateRef('map')
const marks = shallowRef<PointMark[]>([])
const strong = shallowRef<ReadonlySet<number>>(new Set())

const selected = computed(() => (props.row ? markIds(props.row) : []))
// у записи две станции — линия между ними
const line = useStationLine(
  () => {
    const [from, to] = def.name === null ? selected.value : []
    return from !== undefined && to !== undefined ? [from, to] : null
  },
  (station) => marks.value.find((m) => m.id === station)?.point ?? null,
)

// точки загружены; сброшено — загрузить заново
let loaded = false
// записи изменились
let stale = false
// поиск, при котором карта последний раз вписана; null — ещё не вписана
let fitted: string | null = null

const load = useLoad(async () => {
  let search: string
  let found: AdminRow[] | null
  // поиск сменили, пока шла загрузка, — загрузка повторяется по новому
  do {
    search = props.search.trim()
    const needPoints = !loaded
    loaded = true
    try {
      const [rows, points] = await Promise.all([
        search ? fetchAdminAll(props.table, search) : null,
        needPoints ? fetchMarks() : null,
      ])
      found = rows
      if (points) marks.value = points
    } catch (e) {
      if (needPoints) loaded = false
      throw e
    }
  } while (search !== props.search.trim() || !loaded)

  const ids = new Set((found ?? []).flatMap(markIds))
  strong.value = ids
  // прямая линия — по точкам станций: они загрузились
  if (!line.line.value) line.update()
  const first = fitted === null
  if (fitted === search) return
  fitted = search
  // поиск сброшен — масштаб и место прежние
  if (!search && !first) return
  if (search) map.value?.show(marks.value.filter((m) => ids.has(m.id)))
  else map.value?.fit(marks.value)
})

/** id точек записи на карте: сама запись; у перегона — его станции. */
function markIds(row: AdminRow): number[] {
  if (def.name !== null) return [row.id]
  return def.stations.map((field) => row[field]).filter((id) => typeof id === 'number')
}

/** Точки по названию: порядок важности при прореживании. */
async function fetchMarks(): Promise<PointMark[]> {
  const name = def.name
  const [stations, rows] = await Promise.all([
    fetchStationMarks(stale),
    name !== null ? fetchAdminAll(props.table, '') : null,
  ])
  stale = false
  // станции — сами записи списка
  if (name === null || !rows || def.stations.length === 0) return stations
  const points = new Map(stations.map((s) => [s.id, s.point]))
  return rows
    .flatMap((row): PointMark[] => {
      const point = points.get(Number(row[def.stations[0]!]))
      const text = row[name]
      return point && typeof text === 'string' ? [{ id: row.id, point, text }] : []
    })
    .sort((a, b) => a.text.localeCompare(b.text))
}

function pick(mark: PointMark) {
  // повторный щелчок: точка уже выбрана в списке или её название — в поиске
  if (selected.value.includes(mark.id) || props.search.trim() === mark.text.trim()) {
    fitted = ''
    emit('clear')
    return
  }
  // поиск сменит щелчок — вписывать карту по нему не нужно
  fitted = mark.text.trim()
  emit('pick', mark.text)
}

function onOpen(mark: PointMark) {
  if (def.name !== null) emit('open', mark.id)
}

/** Записи изменились: загрузить точки и найденные заново; масштаб и место карты не меняются. */
function reload() {
  loaded = false
  stale = true
  void load.run()
  line.update()
}

defineExpose({ reload })

let timer: ReturnType<typeof setTimeout> | undefined
watch(
  () => props.search,
  () => {
    clearTimeout(timer)
    timer = setTimeout(() => void load.run(), SEARCH_DELAY)
  },
)
onBeforeUnmount(() => clearTimeout(timer))

void load.run()
</script>
