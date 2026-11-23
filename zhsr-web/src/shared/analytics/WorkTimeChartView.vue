<template>
  <ChartView
    :chart="view"
    :empty="rows.length ? '' : t('Выполненных заявок за период нет')"
    class="work-time"
    :class="{ 'work-time--detailed': detailed }"
  >
    <!-- переключатели стоят на месте и во время загрузки -->
    <template v-if="detailed" #tools>
      <div class="work-time__tools q-mb-md">
        <CbSegment v-model="metric" single required :values="METRICS" />
        <PeriodPicker
          v-model:start-date="fromDay"
          v-model:end-date="toDay"
          :label="isMobile ? '' : 'Период:'"
          :presets="MONTH_PRESETS"
          :periods="PERIODS"
          :nullable-start-date="false"
          :nullable-end-date="false"
        />
        <DepotChoice v-model="depot" :depots="(view.data.value ?? []).map((r) => r.depot)" />
      </div>
    </template>

    <template #default>
      <div class="work-time__legend q-mb-sm">
        <span
          v-for="group in groups"
          :key="group.service"
          class="work-time__legend-item"
          :title="title(group.name, group)"
        >
          <i class="work-time__swatch" :style="{ '--service': colorOf(group.service) }" />
          {{ group.name }}
          <span class="work-time__legend-value">{{ detailed ? legendText(group) : valueText(group) }}</span>
        </span>
      </div>

      <div ref="box" class="work-time__box">
        <div
          v-for="tile in tiles"
          :key="tile.key"
          class="work-time__tile"
          :style="{ ...place(tile.rect), '--service': colorOf(tile.service) }"
          :title="tile.title"
        >
          <div class="work-time__fill">
            <template v-if="tile.labelled">
              <span class="work-time__name">{{ tile.name }}</span>
              <span class="work-time__value">{{ detailed ? tileText(tile) : valueText(tile) }}</span>
            </template>
          </div>
        </div>
      </div>
    </template>
  </ChartView>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import type { WorkTimeRow } from '@/api/analytics'
import { fetchWorkTime } from '@/api/analytics'
import { i18n, t } from '@/boot/i18n'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import { presetDates } from '@/shared/components/period/period'
import PeriodPicker from '@/shared/components/period/PeriodPicker.vue'
import { ServiceType } from '@/shared/constants/systemDicts'
import { isMobile } from '@/shared/device'
import { dictManager } from '@/shared/dict/DictManager'
import { dayStart, formatThousands } from '@/shared/format'
import ChartView from './ChartView.vue'
import DepotChoice from './DepotChoice.vue'
import { ALL_DEPOTS } from './depots'
import { PRESETS } from './periods'
import type { TreemapRect } from './treemap'
import { treemap } from './treemap'
import { useChart } from './useChart'

/**
 * Диаграмма «Заявки»: древовидная карта. Область — служба, прямоугольник внутри неё — неисправность;
 * площадь — число выполненных заявок или их суммарное время выполнения.
 *
 * detailed = false — ячейка дашборда: время выполнения за месяц, все депо, подписи крупных прямоугольников
 * с многоточием. detailed = true — окно раскрытой диаграммы: период, депо, показатель, в подписях и время, и количество. Закономерность с цифрами — по всем депо —
 * уходит владельцу событием find.
 */
const props = defineProps<{ detailed: boolean }>()
const emit = defineEmits<{ find: [text: string] }>()

const COUNT = 0
const TIME = 1
/** Чему пропорциональна площадь. */
const METRICS = [
  { id: TIME, text: 'По времени выполнения' },
  { id: COUNT, text: 'По количеству' },
]
/** Готовые периоды: общие без «Сегодня» и «С начала месяца». */
const MONTH_PRESETS = PRESETS.filter((p) => p.value !== 'd1' && p.value !== 'mStart')
/** Кнопки периода; остальные готовые периоды — в «...». */
const PERIODS = ['d7', 'm1', 'prevMonth']
/** Цвет службы постоянный: от отбора и места на карте не зависит. */
const COLORS: Record<number, string> = {
  [ServiceType.TECHNICAL]: '#2a78d6',
  [ServiceType.CLEANING]: '#1baf7a',
  [ServiceType.EQUIPMENT]: '#eda100',
  [ServiceType.INSTRUCTOR]: '#4a3aa7',
  [ServiceType.OPERATION]: '#e34948',
}
/** Наименьший прямоугольник с подписью, rem. */
const LABEL_MIN_W = 4
const LABEL_MIN_H = 2.4
/** Раскрытая диаграмма: уже этой ширины прямоугольник уходит в «Прочие», rem. */
const DETAILED_MIN_W = 5
/** Наименьшая ширина полной подписи («2 заявки, 69,5 часов»), rem. */
const FULL_LABEL_MIN_W = 11

interface Sums {
  requests: number
  minutes: number
}

interface Group extends Sums {
  service: number
  name: string
  value: number
  rect: TreemapRect
}

interface Tile extends Sums {
  key: string
  service: number
  name: string
  title: string
  rect: TreemapRect
  /** подпись помещается */
  labelled: boolean
}

// период по умолчанию — месяц
const month = presetDates(PRESETS.find((p) => p.value === 'm1')!)
const fromDay = ref<string | null>(month.from)
const toDay = ref<string | null>(month.to)
const depot = ref<number>(ALL_DEPOTS)
const metric = ref<number>(TIME)

const view = useChart(
  async () =>
    (
      await Promise.all([
        fetchWorkTime(dayStart(fromDay.value!), dayStart(toDay.value!, 1)),
        dictManager.prepareDict('Depot'),
        dictManager.prepareDict('FaultType'),
      ])
    )[0],
  (text) => emit('find', text),
  (data) => {
    // выбранного депо в новом периоде нет — все депо
    if (!data.some((r) => r.depot === depot.value)) depot.value = ALL_DEPOTS
    return findText(data)
  },
)
const { load } = view

const rows = computed(() => {
  const data = view.data.value ?? []
  return depot.value === ALL_DEPOTS ? data : data.filter((r) => r.depot === depot.value)
})

// размер карты, rem: раскладка и подписи зависят от него
const box = ref<HTMLElement | null>(null)
const size = ref({ w: 0, h: 0 })
const observer = new ResizeObserver(([entry]) => {
  if (!entry) return
  const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
  size.value = { w: entry.contentRect.width / rem, h: entry.contentRect.height / rem }
})
// карты нет, пока данные грузятся или их нет
watch(box, (el) => {
  observer.disconnect()
  if (el) observer.observe(el)
})
onBeforeUnmount(() => observer.disconnect())

function sumOf(list: WorkTimeRow[]): Sums {
  return {
    requests: list.reduce((a, r) => a + r.requests, 0),
    minutes: list.reduce((a, r) => a + r.minutes, 0),
  }
}

function valueOf(sums: Sums): number {
  return metric.value === COUNT ? sums.requests : sums.minutes
}

/** Строки по ключу. */
function groupRows(list: WorkTimeRow[], keyOf: (r: WorkTimeRow) => number): [number, WorkTimeRow[]][] {
  const res = new Map<number, WorkTimeRow[]>()
  for (const row of list) {
    const items = res.get(keyOf(row))
    if (items) items.push(row)
    else res.set(keyOf(row), [row])
  }
  return [...res]
}

/** Суммы строк по ключу — с площадью больше нуля, по убыванию. */
function grouped(list: WorkTimeRow[], keyOf: (r: WorkTimeRow) => number) {
  return groupRows(list, keyOf)
    .map(([id, items]) => {
      const sums = sumOf(items)
      return { id, items, ...sums, value: valueOf(sums) }
    })
    .filter((g) => g.value > 0)
    .sort((a, b) => b.value - a.value || a.id - b.id)
}

const services = computed(() => grouped(rows.value, (r) => r.service))
const total = computed(() => services.value.reduce((a, s) => a + s.value, 0))

const groups = computed<Group[]>(() => {
  const rects = treemap(
    services.value.map((s) => s.value),
    { x: 0, y: 0, ...size.value },
  )
  return services.value.map((s, i) => ({
    service: s.id,
    name: dictManager.getValue('Service', s.id),
    requests: s.requests,
    minutes: s.minutes,
    value: s.value,
    rect: rects[i]!,
  }))
})

type FaultSums = ReturnType<typeof grouped>[number]

/** Прямоугольники службы: показанные неисправности, за ними — «Прочие», если others не пуст. */
interface Layout {
  shown: FaultSums[]
  others: FaultSums[]
  rects: TreemapRect[]
}

function layoutOf(faults: FaultSums[], shownCount: number, rect: TreemapRect): Layout {
  const shown = faults.slice(0, shownCount)
  const others = faults.slice(shownCount)
  const values = shown.map((f) => f.value)
  if (others.length) values.push(others.reduce((a, f) => a + f.value, 0))
  return { shown, others, rects: treemap(values, rect) }
}

function fits(rect: TreemapRect): boolean {
  const minW = props.detailed ? DETAILED_MIN_W : LABEL_MIN_W
  return rect.w >= minW && rect.h >= LABEL_MIN_H
}

/** Раскладка службы: в раскрытой диаграмме неисправности, подписи которых не помещаются, — одним «Прочие». */
function serviceLayout(faults: FaultSums[], rect: TreemapRect): Layout {
  const all = layoutOf(faults, faults.length, rect)
  if (!props.detailed || all.rects.every(fits)) return all
  // в «Прочих» не меньше двух неисправностей
  for (let count = faults.length - 2; count > 0; count--) {
    const layout = layoutOf(faults, count, rect)
    if (layout.rects.every(fits)) return layout
  }
  return faults.length > 1 ? layoutOf(faults, 0, rect) : all
}

const tiles = computed<Tile[]>(() =>
  services.value.flatMap((s, i) => {
    const { rect, name: serviceName } = groups.value[i]!
    const { shown, others, rects } = serviceLayout(
      grouped(s.items, (r) => r.faultType),
      rect,
    )
    const tileOf = (id: number | string, name: string, sums: Sums, place: TreemapRect): Tile => ({
      key: `${s.id}-${id}`,
      service: s.id,
      name,
      title: title(`${serviceName}: ${name}`, sums),
      requests: sums.requests,
      minutes: sums.minutes,
      rect: place,
      labelled: fits(place),
    })
    const res = shown.map((f, k) => tileOf(f.id, dictManager.getValue('FaultType', f.id), f, rects[k]!))
    if (others.length) {
      res.push(tileOf('others', t('Прочие'), sumOf(others.flatMap((f) => f.items)), rects[shown.length]!))
    }
    return res
  }),
)

function colorOf(service: number): string {
  return COLORS[service] ?? '#62666d'
}

/** Место прямоугольника — в процентах карты. */
function place(rect: TreemapRect) {
  const { w, h } = size.value
  // размер ещё не измерен
  if (!w || !h) return { display: 'none' }
  return {
    left: (rect.x / w) * 100 + '%',
    top: (rect.y / h) * 100 + '%',
    width: (rect.w / w) * 100 + '%',
    height: (rect.h / h) * 100 + '%',
  }
}

function percent(share: number): string {
  return Math.round(share * 100) + '%'
}

/** Время в часах, округлённое для показа. */
function roundHours(minutes: number): number {
  const value = minutes / 60
  const digits = value < 1 ? 2 : value < 100 ? 1 : 0
  return Number(value.toFixed(digits))
}

function number(value: number): string {
  return new Intl.NumberFormat(i18n.global.locale.value).format(value)
}

/** Время в часах: «69,5 ч». */
function hours(minutes: number): string {
  return t('{h} ч', { h: number(roundHours(minutes)) })
}

/** «2 заявки». */
function requestsText(sums: Sums): string {
  return t('{n} заявок', { n: formatThousands(sums.requests) }, sums.requests)
}

/** «69,5 часов». */
function hoursText(sums: Sums): string {
  const value = roundHours(sums.minutes)
  return t('{n} часов', { n: number(value) }, value)
}

/** Подпись раскрытой диаграммы: «2 заявки, 69,5 часов», в узком прямоугольнике — «2 · 69,5 ч». */
function tileText(tile: Tile): string {
  if (tile.rect.w < FULL_LABEL_MIN_W) return `${formatThousands(tile.requests)} · ${hours(tile.minutes)}`
  return `${requestsText(tile)}, ${hoursText(tile)}`
}

function valueText(sums: Sums): string {
  return metric.value === COUNT ? formatThousands(sums.requests) : hours(sums.minutes)
}

/** Легенда раскрытой диаграммы: количество, доля и время — «11 заявок (11%), 86,5 часов». */
function legendText(group: Group): string {
  return `${requestsText(group)} (${percent(group.value / total.value)}), ${hoursText(group)}`
}

function title(name: string, sums: Sums): string {
  return [
    name,
    t('Выполнено заявок: {n}', { n: formatThousands(sums.requests) }),
    t('Время выполнения: {value}', { value: hours(sums.minutes) }),
    t('Среднее время на заявку: {value}', { value: hours(sums.minutes / sums.requests) }),
    t('Доля: {value}', { value: percent(valueOf(sums) / total.value) }),
  ].join('\n')
}

/** Закономерность: неисправность, на которую ушло больше всего времени, и её доли во времени и в заявках. */
function findText(data: WorkTimeRow[]): string {
  const all = sumOf(data)
  if (!all.minutes) return ''
  const top = groupRows(data, (r) => r.faultType)
    .map(([id, items]) => ({ id, ...sumOf(items) }))
    .reduce((a, f) => (f.minutes > a.minutes ? f : a))
  return t(
    'Больше всего времени за период ушло на неисправность «{fault}»: {time} времени выполнения и {requests} заявок.',
    {
      fault: dictManager.getValue('FaultType', top.id),
      time: percent(top.minutes / all.minutes),
      requests: percent(top.requests / all.requests),
    },
  )
}

watch([fromDay, toDay], () => void load())

void load()
</script>

<style scoped lang="scss">
.work-time__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.work-time__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1rem;
  color: $zs-text;
  font-size: 0.8rem;
}

.work-time__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.work-time__legend-value {
  color: $zs-text-muted;
}

.work-time__swatch {
  width: 1rem;
  height: 0.8rem;
  border-radius: 0.2rem;
  // тон прямоугольников
  background: color-mix(in srgb, var(--service) 45%, $zs-bg-panel);
}

.work-time__box {
  position: relative;
  flex: 1;
  min-height: 12rem;
  overflow: hidden;
}

.work-time--detailed .work-time__box {
  min-height: 30rem;
}

.work-time__tile {
  position: absolute;
  box-sizing: border-box;
  // промежуток между прямоугольниками — фон панели
  padding: 0.1rem;
}

.work-time__fill {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: 100%;
  border-radius: 0.2rem;
  overflow: hidden;
  // светлый тон службы: поверх него читается тёмный текст
  background: color-mix(in srgb, var(--service) 45%, $zs-bg-panel);
  color: $zs-text;
  font-size: 0.8rem;
  line-height: 1.2;
}

.work-time__fill:not(:empty) {
  padding: 0.2rem 0.4rem;
}

.work-time__tile:hover .work-time__fill {
  background: color-mix(in srgb, var(--service) 65%, $zs-bg-panel);
}

.work-time__name {
  min-height: 0;
  overflow: hidden;
  overflow-wrap: anywhere;
}

// ячейка дашборда: подпись в одну строку с многоточием
.work-time:not(.work-time--detailed) :is(.work-time__name, .work-time__value) {
  flex: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-time__value {
  flex: none;
  font-weight: 600;
}
</style>
