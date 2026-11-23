<template>
  <ChartView
    :chart="view"
    :empty="allRows.length ? '' : t('Выполненных заявок за год мало')"
    class="ridge"
    :class="{ 'ridge--detailed': detailed }"
  >
    <template #default>
      <div v-if="detailed" class="ridge__tools q-mb-md">
        <DepotChoice v-model="depot" :depots="allRows.map((r) => r.depot)" />
      </div>
      <div v-if="detailed" class="ridge__legend q-mb-md">
        <span class="ridge__legend-item"
          ><i class="ridge__swatch" />{{ t('время от взятия в работу до выполнения') }}</span
        >
        <span class="ridge__legend-item">
          <i class="ridge__swatch ridge__swatch--over" />{{ t('доля возвратов выше медианной') }}
        </span>
      </div>

      <div class="ridge__scroll">
        <div class="ridge__grid">
          <span class="ridge__head ridge__head--time" />
          <span class="ridge__head">{{ detailed ? t('Доля возвратов на доработку') : t('Доля возвратов') }}</span>
          <span v-if="detailed" class="ridge__head" />

          <div v-if="detailed" class="ridge__body" :style="{ height: bodyHeight }">
            <span
              v-for="(row, i) in rows"
              :key="row.id"
              class="ridge__name"
              :style="bandStyle(i)"
              :title="rowTitle(row)"
            >
              {{ row.name }}
            </span>
          </div>

          <div class="ridge__body">
            <span class="ridge__fast" :style="{ width: timePos(FAST_MINUTES) + '%' }" />
            <span v-for="tick in TICKS" :key="tick" class="ridge__line" :style="{ left: timePos(tick) + '%' }" />
            <svg class="ridge__svg" :viewBox="`0 0 100 ${units}`" preserveAspectRatio="none">
              <path v-for="(row, i) in rows" :key="row.id" class="ridge__path" :d="ridgePath(row, i)">
                <title v-if="detailed">{{ rowTitle(row) }}</title>
              </path>
            </svg>
            <span v-if="detailed" class="ridge__fast-label">{{ t('быстрее 15 мин') }}</span>
          </div>

          <div class="ridge__body">
            <span
              v-for="(row, i) in rows"
              :key="row.id"
              class="ridge__band"
              :style="bandStyle(i)"
              :title="detailed ? rowTitle(row) : undefined"
            >
              <i
                class="ridge__bar"
                :class="{ 'ridge__bar--over': row.ret > medianReturn }"
                :style="{ width: returnPos(row.ret) }"
              />
            </span>
            <span class="ridge__median" :style="{ left: returnPos(medianReturn) }" />
          </div>

          <div v-if="detailed" class="ridge__body">
            <span
              v-for="(row, i) in rows"
              :key="row.id"
              class="ridge__percent"
              :class="{ 'ridge__percent--over': row.ret > medianReturn }"
              :style="bandStyle(i)"
            >
              {{ percent(row.ret) }}
            </span>
          </div>

          <span v-if="detailed" class="ridge__axis" />
          <span class="ridge__axis">
            <span v-for="tick in TICKS" :key="tick" class="ridge__tick" :style="{ left: timePos(tick) + '%' }">{{
              duration(tick)
            }}</span>
          </span>
          <span class="ridge__axis">
            <span class="ridge__median-label" :style="{ left: returnPos(medianReturn) }">
              {{ t('медианная доля возвратов - {value}', { value: percent(medianReturn) }) }}
            </span>
          </span>
          <span v-if="detailed" class="ridge__axis" />
        </div>
      </div>
    </template>
  </ChartView>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { fetchExecution } from '@/api/analytics'
import { i18n, t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import ChartView from './ChartView.vue'
import DepotChoice from './DepotChoice.vue'
import { ALL_DEPOTS } from './depots'
import { useChart } from './useChart'

/**
 * Диаграмма «Скорость и качество исполнения заявок»: хребет — распределение времени исполнения заявок исполнителя на
 * логарифмической шкале, столбик справа — доля заявок, возвращённых на доработку, вертикальная линия — медиана этой
 * доли по всем исполнителям всех депо. Строки — по возрастанию доли заявок, выполненных быстрее 15 минут.
 *
 * Столбик красный, если доля возвратов исполнителя больше медианы.
 *
 * Линия медианы и шкала столбиков от выбора депо не зависят.
 *
 * detailed = false — ячейка дашборда: исполнители всех депо без ФИО. detailed = true — окно раскрытой диаграммы: выбор
 * депо, легенда, ФИО, доли возвратов, подсказки. Закономерность с цифрами — по всем депо — уходит владельцу
 * событием find.
 */
defineProps<{ detailed: boolean }>()
const emit = defineEmits<{ find: [text: string] }>()

/** Исполнитель показывается, если выполненных заявок не меньше. */
const MIN_DONE = 20
/** Выполнение быстрее стольких минут — подозрение на формальное закрытие. */
const FAST_MINUTES = 15
/** Исполнитель с пиком: доля быстрых выполнений больше. */
const BAD_SHARE = 0.3
/** Деления шкалы времени, минуты. */
const TICKS = [5, 15, 60, 240, 1440, 5760]
/** Логарифмы концов шкалы времени: 1 минута и 10 суток; время за концами — на концах. */
const LOW = 0
const HIGH = Math.log(14400)
/** Число точек хребта и ширина сглаживания в логарифмах времени. */
const POINTS = 120
const SMOOTH = 0.18
/** Высота хребта и место над первой строкой, в строках. */
const PEAK = 2.2
const TOP = 2.4

const depot = ref<number>(ALL_DEPOTS)

const view = useChart(
  async () => (await Promise.all([fetchExecution(), dictManager.prepareDict('Depot')]))[0],
  (text) => emit('find', text),
  findText,
)
const { data, load } = view

interface Row {
  id: number
  name: string
  depot: number | null
  done: number
  median: number
  /** доля заявок, выполненных быстрее FAST_MINUTES */
  fast: number
  /** доля заявок, возвращённых на доработку */
  ret: number
  bad: boolean
  /** точки хребта: 0…1 */
  density: number[]
}

function median(values: number[]): number {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const mid = sorted.length >> 1
  return sorted.length % 2 ? sorted[mid]! : (sorted[mid - 1]! + sorted[mid]!) / 2
}

function densityOf(minutes: number[]): number[] {
  const logs = minutes.map((m) => Math.min(HIGH, Math.log(Math.max(1, m))))
  const list = Array.from({ length: POINTS }, (_, k) => {
    const center = LOW + (k / (POINTS - 1)) * (HIGH - LOW)
    return logs.reduce((sum, v) => sum + Math.exp(-((v - center) ** 2) / (2 * SMOOTH ** 2)), 0)
  })
  const max = Math.max(...list)
  return list.map((v) => v / max)
}

const allRows = computed<Row[]>(() =>
  (data.value ?? [])
    .filter((r) => r.minutes.length >= MIN_DONE)
    .map((r) => {
      const done = r.minutes.length
      const fast = r.minutes.filter((m) => m < FAST_MINUTES).length / done
      return {
        id: r.executorUsr.id,
        name: r.executorUsr.name,
        depot: r.depot,
        done,
        median: median(r.minutes),
        fast,
        ret: r.returned / done,
        bad: fast > BAD_SHARE,
        density: densityOf(r.minutes),
      }
    })
    .sort((a, b) => a.fast - b.fast || a.id - b.id),
)

const rows = computed(() =>
  depot.value === ALL_DEPOTS ? allRows.value : allRows.value.filter((r) => r.depot === depot.value),
)

const medianReturn = computed(() => median(allRows.value.map((r) => r.ret)))

/** Конец шкалы столбиков: ближайшие сверху 5%. */
const returnMax = computed(() => Math.max(0.05, Math.ceil(Math.max(...allRows.value.map((r) => r.ret)) * 20) / 20))

/** Высота диаграммы в строках. */
const units = computed(() => TOP + rows.value.length)
const bodyHeight = computed(() => (units.value * 1.6).toFixed(1) + 'rem')

/** Положение времени на шкале, проценты. */
function timePos(minutes: number): number {
  return ((Math.log(minutes) - LOW) / (HIGH - LOW)) * 100
}

function returnPos(share: number): string {
  return (share / returnMax.value) * 100 + '%'
}

/** Полоса строки: нижний край — основание хребта. */
function bandStyle(i: number) {
  return { top: ((TOP + i) / units.value) * 100 + '%', height: 100 / units.value + '%' }
}

function ridgePath(row: Row, i: number): string {
  const base = TOP + i + 1
  const points = row.density.map((v, k) => `${((k / (POINTS - 1)) * 100).toFixed(2)},${(base - v * PEAK).toFixed(3)}`)
  return `M0,${base}L${points.join('L')}L100,${base}Z`
}

function number(v: number): string {
  return new Intl.NumberFormat(i18n.global.locale.value, { maximumFractionDigits: 1 }).format(v)
}

function percent(share: number): string {
  return Math.round(share * 100) + '%'
}

function duration(minutes: number): string {
  if (minutes < 60) return t('{m} мин', { m: Math.round(minutes) })
  if (minutes < 1440) return t('{h} ч', { h: number(minutes / 60) })
  return t('{n} сут', { n: number(minutes / 1440) })
}

function rowTitle(row: Row): string {
  return [
    row.name,
    row.depot === null ? t('Депо не задано') : dictManager.getValue('Depot', row.depot),
    t('Выполнено заявок: {n}', { n: row.done }),
    t('Медиана времени исполнения: {value}', { value: duration(row.median) }),
    t('Быстрее 15 минут: {value}', { value: percent(row.fast) }),
    t('Возвращено на доработку: {value}', { value: percent(row.ret) }),
  ].join('\n')
}

/** Закономерность: исполнители с пиком быстрых выполнений и доля возвратов у них и у остальных. */
function findText(): string {
  const bad = allRows.value.filter((r) => r.bad)
  const rest = allRows.value.filter((r) => !r.bad)
  if (!bad.length || !rest.length) return ''
  const mean = (list: Row[]) => list.reduce((a, r) => a + r.ret, 0) / list.length
  return t(
    'Исполнителей, у которых больше 30% заявок выполнены быстрее 15 минут после взятия в работу: {n}. У них возвращают на доработку в среднем {bad} заявок, у остальных - {rest}. Медиана времени исполнения такой пик не показывает.',
    { n: bad.length, bad: percent(mean(bad)), rest: percent(mean(rest)) },
  )
}

void load()
</script>

<style scoped lang="scss">
.ridge__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1rem;
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.ridge__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.ridge__swatch {
  width: 1rem;
  height: 0.8rem;
  border-radius: 0.2rem;
  background: #6da7ec;

  &--over {
    background: #d03b3b;
  }
}

.ridge__scroll {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

// ячейка дашборда: строки делят высоту поровну, сколько бы исполнителей ни было
.ridge__grid {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 3fr) minmax(0, 1fr);
  grid-template-rows: auto minmax(0, 1fr) auto;
  column-gap: 0.8rem;
  min-height: 0;
}

// окно диаграммы: строки фиксированной высоты, прокручивается страница
.ridge--detailed .ridge__grid {
  flex: none;
  grid-template-columns: max-content minmax(0, 1fr) 14rem 3rem;
  grid-template-rows: auto auto auto;
}

.ridge__head {
  align-self: end;
  color: $zs-text-muted;
  font-size: 0.8rem;
}

// пустое место над ФИО и хребтами
.ridge--detailed .ridge__head--time {
  grid-column: span 2;
}

.ridge__body {
  position: relative;
  min-height: 0;
}

.ridge--detailed .ridge__body:first-of-type {
  min-width: 12rem;
}

.ridge__name,
.ridge__percent,
.ridge__band {
  position: absolute;
  right: 0;
  left: 0;
}

.ridge__name,
.ridge__percent {
  display: flex;
  align-items: flex-end;
  color: $zs-text-muted;
  font-size: 0.8rem;
  line-height: 1.2;
  white-space: nowrap;
}

.ridge__name {
  overflow: hidden;
}

.ridge__percent--over {
  color: $zs-text;
  font-weight: 600;
}

.ridge__fast {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  // наклон штриховки - вперёд: /
  background: repeating-linear-gradient(135deg, #f6d5d5 0 0.1rem, transparent 0.1rem 0.5rem);
}

.ridge__fast-label {
  position: absolute;
  top: 0.2rem;
  left: 0.3rem;
  color: $zs-text-muted;
  font-size: 0.7rem;
  pointer-events: none;
}

.ridge__line {
  position: absolute;
  top: 0;
  bottom: 0;
  border-left: 0.1rem solid $zs-border-row;
}

.ridge__svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.ridge__path {
  fill: #6da7ec;
  fill-opacity: 0.6;
  stroke: $zs-bg-panel;
  stroke-width: 0.1rem;
  vector-effect: non-scaling-stroke;
}

.ridge__bar {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 60%;
  border-radius: 0 0.2rem 0.2rem 0;
  background: #6da7ec;

  &--over {
    background: #d03b3b;
  }
}

// пунктир продолжается ниже столбиков, до своей подписи
.ridge__median {
  position: absolute;
  top: 0;
  bottom: -0.6rem;
  width: 0.1rem;
  background: repeating-linear-gradient(to bottom, $zs-text-muted 0 0.4rem, transparent 0.4rem 0.6rem);
  transform: translateX(-50%);
  pointer-events: none;
}

.ridge__axis {
  position: relative;
  height: 1.8rem;
  color: $zs-text-muted;
  font-size: 0.7rem;
}

.ridge__tick {
  position: absolute;
  top: 0.2rem;
  transform: translateX(-50%);
  white-space: nowrap;
}

// подпись начинается у линии: линия стоит ближе к левому краю
.ridge__median-label {
  position: absolute;
  top: 0.7rem;
  transform: translateX(-20%);
  white-space: nowrap;
}

// ячейка дашборда: колонка возвратов уже подписи - подпись прижата к правому краю, ниже делений времени
.ridge:not(.ridge--detailed) {
  .ridge__axis {
    height: 2.2rem;
  }

  .ridge__median {
    bottom: -1rem;
  }

  .ridge__median-label {
    right: 0;
    left: auto !important;
    top: 1.1rem;
    transform: none;
  }
}
</style>
