<template>
  <ChartView
    :chart="view"
    :empty="hasRequests ? '' : t('Заявок за год нет')"
    class="flow"
    :class="{ 'flow--detailed': detailed }"
  >
    <!-- переключатели стоят на месте и во время загрузки -->
    <template v-if="detailed" #tools>
      <div class="flow__tools q-mb-md">
        <CbSegment v-model="metric" single required :values="METRICS" />
        <CbSegment v-model="service" single required :values="serviceItems" :max-button-count="6" @change="load" />
      </div>
    </template>

    <template #default="{ data: loaded }">
      <div v-if="detailed" class="flow__head q-mb-md">
        {{ t(METRICS[metric]!.text) }}
        <span class="flow__legend flow__legend--inline">
          <span class="flow__legend-item">
            {{ t('меньше') }}
            <span class="flow__ramp"><i v-for="n in 7" :key="n" :class="'flow--c' + n" /></span>
            {{ t('больше') }}
          </span>
          <span class="flow__legend-item"><i class="flow--c0" />{{ t(zeroText) }}</span>
        </span>
      </div>

      <div class="flow__grid" :style="{ gridTemplateColumns: columns }">
        <!-- количество: недельные итоги столбцами -->
        <span v-if="!detailed" class="flow__head" style="grid-area: 1 / 1 / 2 / -1">{{
          t(METRICS[metric]!.text)
        }}</span>
        <span
          v-for="(week, w) in weeks"
          :key="'w' + w"
          class="flow__slot"
          :style="{ gridArea: area(2, w) }"
          :title="detailed ? weekTitle(w) : undefined"
        >
          <i class="flow__bar" :style="{ height: share(week.value, weekMax) }" />
        </span>
        <span
          v-for="month in months"
          :key="'m' + month.week"
          class="flow__month"
          :style="{ gridRow: 3, gridColumn: `${month.week + 2} / span ${month.span}` }"
        >
          {{ month.name }}
        </span>

        <!-- календарь: столбец — неделя, строка — день недели -->
        <span v-for="(name, d) in weekdays" :key="'d' + d" class="flow__label" :style="{ gridArea: `${d + 4} / 1` }">
          <!-- ячейка дашборда: подписи через одну -->
          <template v-if="detailed || d % 2 === 0">{{ name }}</template>
        </span>
        <i
          v-for="(day, i) in days"
          :key="day.day"
          class="flow__day"
          :class="'flow--c' + color(day.value)"
          :style="{ gridArea: area(4 + (i % 7), Math.floor(i / 7)) }"
          :title="detailed ? dayTitle(day) : undefined"
        />

        <!-- справа — среднее по дням недели -->
        <template v-if="detailed">
          <span class="flow__profile-head" :style="{ gridArea: area(3, weeks.length) }">{{ t('в среднем') }}</span>
          <span
            v-for="(v, d) in profile"
            :key="'p' + d"
            class="flow__profile"
            :style="{ gridArea: area(4 + d, weeks.length) }"
          >
            <i :style="{ width: `calc((100% - 3rem) * ${v / profileMax})` }" />{{ number(v) }}
          </span>
        </template>

        <!-- сроки исполнения: те же недели -->
        <span class="flow__head flow__head--terms" style="grid-area: 11 / 1 / 12 / -1">
          {{ t('Сроки исполнения') }}
          <span v-if="detailed" class="flow__legend flow__legend--inline">
            <span class="flow__legend-item"><i class="flow__swatch-median" />{{ t('медиана срока') }}</span>
            <span class="flow__legend-item"
              ><i class="flow__swatch-dot" />{{ t('типичные заявки: от 10% до 90%') }}</span
            >
          </span>
        </span>
        <span class="flow__axis" :style="{ gridRow: 12, gridColumn: `1 / ${weeks.length + 2}` }">
          <span v-for="tick in ticks" :key="tick" class="flow__tick" :style="{ bottom: share(tick, termMax) }">
            <span class="flow__tick-text">{{ t('{h} ч', { h: tick }) }}</span>
          </span>
        </span>
        <span
          v-for="(week, w) in loaded.weeks"
          :key="'t' + week.week"
          class="flow__slot"
          :style="{ gridArea: area(12, w) }"
          :title="detailed ? termTitle(week) : undefined"
        >
          <template v-if="week.median !== null">
            <svg class="flow__dots" viewBox="0 0 1 1" preserveAspectRatio="none">
              <path :d="dotsPath(week.hours)" />
            </svg>
            <i class="flow__median" :style="{ bottom: share(week.median, termMax) }" />
          </template>
        </span>
      </div>
    </template>
  </ChartView>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import type { RequestsChart, RequestsDay, RequestsWeek } from '@/api/analytics'
import { fetchRequestsChart } from '@/api/analytics'
import { i18n, t } from '@/boot/i18n'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import { dictChoices } from '@/shared/dict/dictChoices'
import ChartView from './ChartView.vue'
import { useChart } from './useChart'

/**
 * Диаграмма «Заявки и неисправности»: календарь года (столбец — неделя, строка — день недели, цвет — число заявок),
 * над ним недельные итоги, под ним на тех же неделях — сроки исполнения заявок, поданных за неделю: точка — типичная
 * заявка (без 10% самых быстрых и 10% самых долгих),
 * отметка — медиана.
 *
 * detailed = false — ячейка дашборда: число заявок всех служб. detailed = true — окно раскрытой диаграммы: выбор
 * показателя и службы, легенды, среднее по дням недели, подсказки. Закономерность с цифрами уходит владельцу событием
 * find.
 */
const props = defineProps<{ detailed: boolean }>()
const emit = defineEmits<{ find: [text: string] }>()

const COUNT = 0
const RATE = 1
/** Показатели; id — место в списке. */
const METRICS = [
  { id: COUNT, text: 'Количество заявок' },
  { id: RATE, text: 'Заявок на 10 рейсов' },
]
/** «Все службы» в выборе службы. */
const ALL = 0
/** Деления шкалы сроков, часы. */
const TERM_STEPS = [1, 2, 6, 12, 24, 48, 72, 120, 240, 480]

const metric = ref<number>(COUNT)
const service = ref<number>(ALL)

const view = useChart(
  () => fetchRequestsChart(service.value === ALL ? null : service.value),
  (text) => emit('find', text),
  findText,
)
const { data, load } = view

interface Day extends RequestsDay {
  /** значение показателя; null — рейсов не было, показатель на рейсы не считается */
  value: number | null
}

const serviceItems = computed(() => [{ id: ALL, text: 'Все службы' }, ...dictChoices('Service')])

const hasRequests = computed(() => data.value?.days.some((d) => d.requests > 0) ?? false)

const zeroText = computed(() => (metric.value === RATE ? 'нет заявок или рейсов' : 'нет заявок'))

function total(days: RequestsDay[]) {
  return {
    requests: days.reduce((a, d) => a + d.requests, 0),
    trips: days.reduce((a, d) => a + d.trips, 0),
  }
}

/** Значение показателя по заявкам и рейсам. */
function valueOf(requests: number, trips: number): number | null {
  if (metric.value === COUNT) return requests
  return trips ? (requests / trips) * 10 : null
}

const days = computed<Day[]>(() => (data.value?.days ?? []).map((d) => ({ ...d, value: valueOf(d.requests, d.trips) })))

const weeks = computed(() =>
  (data.value?.weeks ?? []).map((week, w) => {
    const sums = total(days.value.slice(w * 7, w * 7 + 7))
    return { week: week.week, ...sums, value: valueOf(sums.requests, sums.trips) ?? 0 }
  }),
)

const weekMax = computed(() => Math.max(...weeks.value.map((w) => w.value)))

/** Среднее по дням недели, с понедельника. */
const profile = computed(() =>
  weekdays.value.map((_, d) => {
    const list = days.value.filter((_day, i) => i % 7 === d)
    const sums = total(list)
    const value = valueOf(sums.requests, sums.trips) ?? 0
    return metric.value === COUNT ? value / list.length : value
  }),
)
const profileMax = computed(() => Math.max(...profile.value) || 1)

/** Границы семи ступеней цвета: седьмые доли ненулевых значений. */
const bounds = computed(() => {
  const list = days.value.map((d) => d.value ?? 0).filter((v) => v > 0)
  list.sort((a, b) => a - b)
  return [1, 2, 3, 4, 5, 6].map((n) => quantile(list, n / 7))
})

function quantile(sorted: number[], q: number): number {
  if (!sorted.length) return 0
  const position = (sorted.length - 1) * q
  const low = Math.floor(position)
  return sorted[low]! + (sorted[Math.ceil(position)]! - sorted[low]!) * (position - low)
}

function color(value: number | null): number {
  if (!value) return 0
  return 1 + bounds.value.filter((b) => value > b).length
}

const termStep = computed(() => {
  const max = Math.max(1, ...(data.value?.weeks ?? []).map((w) => w.hours[w.hours.length - 1] ?? 0))
  return TERM_STEPS.find((step) => max / step <= 4) ?? Math.ceil(max / 4)
})
const termMax = computed(() => {
  const max = Math.max(1, ...(data.value?.weeks ?? []).map((w) => w.hours[w.hours.length - 1] ?? 0))
  return Math.ceil(max / termStep.value) * termStep.value
})
const ticks = computed(() => {
  const list: number[] = []
  for (let v = 0; v <= termMax.value; v += termStep.value) list.push(v)
  return list
})

const columns = computed(() => `2.6rem repeat(${weeks.value.length}, minmax(0, 1fr))` + (props.detailed ? ' 9rem' : ''))

/** Место ячейки: строка сетки и неделя (первый столбец сетки — подписи). */
function area(row: number, week: number): string {
  return `${row} / ${week + 2}`
}

function share(value: number, max: number): string {
  return (max ? (value / max) * 100 : 0) + '%'
}

/**
 * Точки заявок недели одной линией: отрезок нулевой длины с круглыми концами — точка. Точки разведены по ширине недели
 * (место зависит от порядка), иначе близкие сроки сливаются.
 */
function dotsPath(hours: number[]): string {
  return hours
    .map((h, i) => `M${(0.2 + ((i * 0.618) % 1) * 0.6).toFixed(3)},${(1 - h / termMax.value).toFixed(4)}h0`)
    .join('')
}

/** Дата без времени — местная полночь. */
function toDate(day: string): Date {
  return new Date(day + 'T00:00:00')
}

function format(day: string, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(i18n.global.locale.value, options).format(toDate(day))
}

function number(v: number): string {
  return new Intl.NumberFormat(i18n.global.locale.value, { maximumFractionDigits: 1 }).format(v)
}

const weekdays = computed(() => (data.value?.days ?? []).slice(0, 7).map((d) => format(d.day, { weekday: 'short' })))

/** Подписи месяцев: месяц стоит над неделями, четверг которых в него попадает; узкий месяц не подписывается; ячейка дашборда — через один. */
const months = computed(() => {
  const list: { week: number; span: number; month: number; name: string }[] = []
  weeks.value.forEach((w, i) => {
    const thursday = toDate(w.week)
    thursday.setDate(thursday.getDate() + 3)
    const last = list[list.length - 1]
    if (last?.month === thursday.getMonth()) last.span++
    else {
      const name = new Intl.DateTimeFormat(i18n.global.locale.value, { month: 'short' }).format(thursday)
      list.push({ week: i, span: 1, month: thursday.getMonth(), name })
    }
  })
  return list.filter((m) => m.span > 2).filter((_, i) => props.detailed || i % 2 === 0)
})

function countLines(requests: number, trips: number): string[] {
  const lines = [t('Заявок: {n}', { n: requests }), t('Рейсов: {n}', { n: trips })]
  if (trips) lines.push(t('Заявок на 10 рейсов: {n}', { n: number((requests / trips) * 10) }))
  return lines
}

function dayTitle(day: Day): string {
  const date = format(day.day, { day: 'numeric', month: 'long', year: 'numeric', weekday: 'short' })
  return [date, ...countLines(day.requests, day.trips)].join('\n')
}

function weekHead(week: string): string {
  return t('Неделя с {date}', { date: format(week, { day: 'numeric', month: 'long', year: 'numeric' }) })
}

function weekTitle(w: number): string {
  const week = weeks.value[w]!
  return [weekHead(week.week), ...countLines(week.requests, week.trips)].join('\n')
}

function termTitle(week: RequestsWeek): string {
  const lines = [weekHead(week.week), t('Исполнено заявок: {n}', { n: week.done })]
  if (week.median !== null) {
    lines.push(t('Медиана срока: {h} ч', { h: number(week.median) }))
  }
  return lines.join('\n')
}

/** Закономерность: доля заявок в четверти самых загруженных недель и срок исполнения в эти недели и в остальные. */
function findText(chart: RequestsChart): string {
  // последняя неделя не закончена
  const list = chart.weeks.slice(0, -1).map((week, w) => ({
    requests: total(chart.days.slice(w * 7, w * 7 + 7)).requests,
    median: week.median,
  }))
  list.sort((a, b) => b.requests - a.requests)
  const topCount = Math.round(list.length / 4)
  const all = list.reduce((a, w) => a + w.requests, 0)
  const mean = (part: typeof list) => {
    const values = part.map((w) => w.median).filter((m): m is number => m !== null)
    return values.length ? values.reduce((a, b) => a + b, 0) / values.length : null
  }
  const top = list.slice(0, topCount)
  const busyTerm = mean(top)
  const restTerm = mean(list.slice(topCount))
  if (!all || busyTerm === null || restTerm === null) return ''
  return t(
    'На четверть самых загруженных недель приходится {share}% заявок. Медиана срока исполнения в эти недели - в среднем {busy} ч, в остальные - {rest} ч.',
    {
      share: Math.round((top.reduce((a, w) => a + w.requests, 0) / all) * 100),
      busy: number(busyTerm),
      rest: number(restTerm),
    },
  )
}

void load()
</script>

<style scoped lang="scss">
.flow__tools {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
}

.flow__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1rem;
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 400;

  // легенда в одной строке с подзаголовком, после него
  &--inline {
    display: inline-flex;
    margin-left: 1rem;
  }
}

.flow__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;

  i {
    width: 1rem;
    height: 0.8rem;
    border-radius: 0.2rem;
  }
}

.flow__ramp {
  display: inline-flex;
  gap: 0.1rem;
}

// строки: заголовок, недельные итоги, месяцы, 7 дней, заголовок, сроки исполнения
.flow__grid {
  display: grid;
  flex: 1;
  grid-template-rows: auto minmax(0, 3fr) auto repeat(7, auto) auto minmax(0, 6fr);
  gap: 0.1rem;
  min-height: 0;
}

.flow--detailed .flow__grid {
  flex: none;
  grid-template-rows: auto 5rem auto repeat(7, auto) auto 12rem;
  gap: 0.2rem;
}

.flow__head {
  color: $zs-text-muted;
  font-size: 0.7rem;

  &--terms {
    margin: 1rem 0 0.6rem;
  }
}

.flow--detailed .flow__head {
  color: $zs-text;
  font-size: 0.9rem;
  font-weight: 600;

  &--terms {
    margin: 1rem 0 0.8rem;
  }
}

.flow__slot {
  position: relative;
  min-height: 0;
}

.flow__bar {
  position: absolute;
  right: 10%;
  bottom: 0;
  left: 10%;
  border-radius: 0.1rem 0.1rem 0 0;
  background: #3987e5;
}

.flow__month,
.flow__profile-head {
  overflow: hidden;
  color: $zs-text-muted;
  font-size: 0.7rem;
  white-space: nowrap;
}

.flow__profile-head {
  padding-left: 0.8rem;
}

// подпись не задаёт высоту строки: высоту задают квадраты дней
.flow__label {
  align-self: center;
  height: 0;
  color: $zs-text-muted;
  font-size: 0.6rem;
  line-height: 0;
}

// ячейка дашборда: подпись дня — у самых квадратов, подпись месяца занимает место пропущенной соседней
.flow:not(.flow--detailed) {
  .flow__label {
    padding-right: 0.3rem;
    text-align: right;
  }

  .flow__month {
    overflow: visible;
  }
}

.flow--detailed .flow__label {
  font-size: 0.7rem;
}

.flow__day {
  align-self: start;
  aspect-ratio: 1;
  border-radius: 0.1rem;
}

.flow--detailed .flow__day {
  border-radius: 0.2rem;
}

.flow__profile {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding-left: 0.8rem;
  color: $zs-text-muted;
  font-size: 0.7rem;
  line-height: 1;

  i {
    height: 0.8rem;
    border-radius: 0 0.2rem 0.2rem 0;
    background: #9aa0a8;
  }
}

.flow__axis {
  position: relative;
  min-height: 0;
}

.flow__tick {
  position: absolute;
  right: 0;
  left: 2.6rem;
  border-bottom: 0.1rem solid $zs-border-row;
}

.flow__tick-text {
  position: absolute;
  right: 100%;
  bottom: 0;
  padding-right: 0.3rem;
  color: $zs-text-muted;
  font-size: 0.7rem;
  line-height: 1;
  white-space: nowrap;
  transform: translateY(50%);
}

// нижнее деление: подпись не выходит за нижний край
.flow__tick:first-child .flow__tick-text {
  transform: none;
}

.flow__dots {
  position: absolute;
  inset: 0;
  overflow: visible;
  width: 100%;
  height: 100%;

  path {
    fill: none;
    stroke: #f9bd9f;
    stroke-linecap: round;
    stroke-width: 0.2rem;
    // точка круглая при любой ширине недели
    vector-effect: non-scaling-stroke;
  }
}

.flow--detailed .flow__dots path {
  stroke-width: 0.4rem;
}

.flow__median {
  position: absolute;
  right: 0;
  left: 0;
  height: 0.2rem;
  border-radius: 0.1rem;
  background: #9c3d16;
  transform: translateY(50%);
}

.flow__legend-item .flow__swatch-dot {
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 50%;
  background: #f9bd9f;
}

.flow__legend-item .flow__swatch-median {
  height: 0.2rem;
  background: #9c3d16;
}

// число заявок: один цвет от светлого к тёмному
.flow--c0 {
  background: #f0f1f3;
}
.flow--c1 {
  background: #cde2fb;
}
.flow--c2 {
  background: #9ec5f4;
}
.flow--c3 {
  background: #6da7ec;
}
.flow--c4 {
  background: #3987e5;
}
.flow--c5 {
  background: #256abf;
}
.flow--c6 {
  background: #184f95;
}
.flow--c7 {
  background: #0d366b;
}
</style>
