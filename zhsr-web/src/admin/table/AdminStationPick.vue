<template>
  <MarksMap
    ref="map"
    :marks="marks"
    :keep="keep"
    :strong="strong"
    :selected="selected"
    :line="line.line.value"
    :source="load.loading || load.error ? load : line.load"
    @pick="onPick"
  />
</template>

<script setup lang="ts">
import { computed, shallowRef, useTemplateRef } from 'vue'

import type { PointMark } from '@/shared/components/map/mapMarks'
import { nearMarks } from '@/shared/components/map/mapMarks'
import MarksMap from '@/shared/components/map/MarksMap.vue'
import { useDialogEdit } from '@/shared/components/dialog/dialogEdit'
import { useLoad } from '@/shared/loadable'
import { fetchStationMarks, useStationLine } from './adminMaps'

/**
 * Карта диалога записи со станциями: щелчок по станции — событие pick, карта приближается к ней. Станции полей (stations) выделены, станция
 * поля, куда она подставится (active), выбрана; полей два — между их станциями линия.
 * Станции прорежены, рядом с выбранными кружки — все, подписи прорежены.
 */
const props = defineProps<{
  /** станции полей по порядку; null — поле пустое */
  stations: (number | null)[]
  /** номер поля, в которое подставится станция */
  active: number
}>()
const emit = defineEmits<{ pick: [station: number] }>()
const edited = useDialogEdit()

/** Крупнее этого карта при открытии станцию не показывает: рядом видны соседние. */
const OPEN_ZOOM = 9

const map = useTemplateRef('map')
const marks = shallowRef<PointMark[]>([])
const strong = computed(() => new Set(props.stations.filter((id) => id !== null)))
const keep = computed(() =>
  nearMarks(
    marks.value,
    marks.value.filter((m) => strong.value.has(m.id)).map((m) => m.point),
  ),
)
const selected = computed(() => {
  const id = props.stations[props.active]
  return id === null || id === undefined ? [] : [id]
})

const line = useStationLine(
  () => {
    const [from, to] = props.stations
    return from && to && from !== to ? [from, to] : null
  },
  (station) => marks.value.find((m) => m.id === station)?.point ?? null,
)

const load = useLoad(async () => {
  marks.value = await fetchStationMarks()
  // прямая линия — по точкам станций: они загрузились
  if (!line.line.value) line.update()
  const shown = marks.value.filter((m) => strong.value.has(m.id))
  if (shown.length > 0) map.value?.fit(shown, OPEN_ZOOM)
  else map.value?.fit(marks.value)
})

function onPick(mark: PointMark) {
  emit('pick', mark.id)
  edited()
  map.value?.focus(mark)
}

void load.run()
</script>
