<template>
  <ChartView
    :chart="view"
    :empty="allRows.length ? '' : t('Рейсов за год нет')"
    class="overtime"
    :class="{ 'overtime--detailed': detailed }"
  >
    <template #default="{ data: loaded }">
      <div v-if="detailed" class="overtime__tools q-mb-md">
        <DepotChoice v-model="depot" :depots="allRows.map((r) => r.depot)" />
        <span class="overtime__legend">
          {{ t('недогрузка') }}
          <span class="overtime__ramp">
            <i v-for="lv in LEVELS" :key="lv" :class="'overtime--l' + lv" />
          </span>
          {{ t('переработка') }}
          <i class="overtime--lnone q-ml-md" />
          {{ t('нет рейсов') }}
          <span class="q-ml-md">{{ t('норма {norm} ч ± 8%', { norm: loaded.norm }) }}</span>
        </span>
      </div>

      <div class="overtime__grid" :style="{ gridTemplateColumns: columns }">
        <span v-if="detailed" class="overtime__month" />
        <span v-for="(month, i) in monthNames" :key="'m' + i" class="overtime__month">{{ month }}</span>
        <span v-if="detailed" class="overtime__month overtime__month--total">
          {{ t('часов за 11 полных месяцев') }}
          <span class="overtime__scale">
            <span v-for="tick in ticks" :key="tick" class="overtime__tick" :style="{ left: pos(tick) }">{{
              tick
            }}</span>
          </span>
        </span>

        <template v-for="row in rows" :key="row.key">
          <span v-if="detailed" class="overtime__name" :title="row.name">
            {{ row.name }}<template v-if="row.lnp"> ({{ t('ЛНП') }})</template>
          </span>
          <i
            v-for="(h, i) in row.hours"
            :key="i"
            class="overtime__cell"
            :class="'overtime--l' + level(h)"
            :title="cellTitle(row, i, h)"
          />
          <span
            v-if="detailed"
            class="overtime__total"
            :title="t('{hours} ч при нормативе {norm} ч', { hours: formatHours(row.total), norm: normTotal })"
          >
            <i class="overtime__bar" :style="{ width: pos(row.total) }" />
            <i class="overtime__axis" />
            <i class="overtime__norm" :style="{ left: pos(normTotal) }" />
          </span>
        </template>

        <!-- подпись норматива — под линией, в колонке итога -->
        <span v-if="detailed" class="overtime__norm-label" :style="{ gridColumn: '-2 / -1' }">
          <span :style="{ left: pos(normTotal) }">{{ t('норматив, {hours} часов', { hours: normTotal }) }}</span>
        </span>
      </div>
    </template>
  </ChartView>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { CREW_DUTY_LNP, fetchOvertime } from '@/api/analytics'
import { i18n, t } from '@/boot/i18n'
import { formatHours } from '@/shared/format'
import { dictManager } from '@/shared/dict/DictManager'
import ChartView from './ChartView.vue'
import DepotChoice from './DepotChoice.vue'
import { ALL_DEPOTS } from './depots'
import { useChart } from './useChart'

/**
 * Диаграмма «Переработки и недоработки»: тепловая лента — строка сотрудник, столбец месяц, цвет — отклонение
 * часов от нормы. Строки — по убыванию часов за 11 полных месяцев (текущий месяц не закончен).
 *
 * Состав выбора депо — депо сотрудников из ответа сервера.
 *
 * Месяц без рейсов — белый квадрат с тонкой рамкой.
 *
 * detailed = false — ячейка дашборда: строки сотрудников всех депо, делящие высоту ячейки, без ФИО. detailed =
 * true — окно раскрытой диаграммы (строки фиксированной высоты, прокручивается страница): выбор депо, легенда, ФИО, итог часов полосой на шкале с пунктиром норматива,
 * подсказка по ячейке. Закономерность с цифрами
 * уходит владельцу событием find.
 */
const props = defineProps<{ detailed: boolean }>()
const emit = defineEmits<{ find: [text: string] }>()

/** Уровни цвета: отрицательные — недогрузка, 0 — норма ± 8%, положительные — переработка. */
const LEVELS = [-3, -2, -1, 0, 1, 2, 3]
/** Число полных месяцев: последний месяц данных — текущий, не закончен. */
const FULL = 11

const depot = ref<number>(ALL_DEPOTS)

const view = useChart(
  async () => (await Promise.all([fetchOvertime(), dictManager.prepareDict('Depot')]))[0],
  (text) => emit('find', text),
  findText,
)
const { data, load } = view

interface Row {
  key: string
  name: string
  lnp: boolean
  depot: number | null
  hours: number[]
  total: number
}

const allRows = computed<Row[]>(() =>
  (data.value?.rows ?? [])
    .map((r) => ({
      key: r.usr.id + ':' + r.crewDutyType,
      name: r.usr.name,
      lnp: r.crewDutyType === CREW_DUTY_LNP,
      depot: r.depot,
      hours: r.hours,
      total: r.hours.slice(0, FULL).reduce((a, b) => a + b, 0),
    }))
    .sort((a, b) => b.total - a.total),
)

const rows = computed(() =>
  depot.value === ALL_DEPOTS ? allRows.value : allRows.value.filter((r) => r.depot === depot.value),
)

/** Норматив часов за 11 полных месяцев. */
const normTotal = computed(() => (data.value?.norm ?? 0) * FULL)

/** Шаг шкалы итога — круглый, чтобы делений было не больше 6. */
const tickStep = computed(() => {
  const max = Math.max(normTotal.value, ...rows.value.map((r) => r.total))
  return [250, 500, 1000, 2000, 5000].find((step) => max / step <= 5) ?? 10000
})

/** Конец шкалы итога: деление после наибольшего итога и норматива. */
const scaleMax = computed(() => {
  const max = Math.max(1, normTotal.value, ...rows.value.map((r) => r.total))
  return Math.ceil(max / tickStep.value) * tickStep.value
})

const ticks = computed(() => {
  const list: number[] = []
  for (let v = 0; v <= scaleMax.value; v += tickStep.value) list.push(v)
  return list
})

/** Положение часов на шкале итога, в процентах ширины колонки. */
function pos(hours: number): string {
  return (hours / scaleMax.value) * 100 + '%'
}

const monthNames = computed(() => {
  const formatter = new Intl.DateTimeFormat(i18n.global.locale.value, { month: 'short' })
  return (data.value?.months ?? []).map((m) => formatter.format(new Date(m)))
})

const columns = computed(() => (props.detailed ? 'max-content repeat(12, 3rem) 16rem' : 'repeat(12, minmax(0, 1fr))'))

function level(h: number): number | string {
  if (h === 0) return 'none'
  const norm = data.value?.norm ?? 1
  const d = (h - norm) / norm
  if (Math.abs(d) < 0.08) return 0
  if (d > 0) return d > 0.35 ? 3 : d > 0.2 ? 2 : 1
  return d < -0.5 ? -3 : d < -0.25 ? -2 : -1
}

function cellTitle(row: Row, i: number, h: number): string {
  const month = new Intl.DateTimeFormat(i18n.global.locale.value, { month: 'long', year: 'numeric' }).format(
    new Date(data.value!.months[i]!),
  )
  let text =
    `${row.name}${row.lnp ? ' (' + t('ЛНП') + ')' : ''}\n` +
    t('{month}: {hours} ч при норме {norm} ч', { month, hours: formatHours(h), norm: data.value!.norm })
  if (i === FULL) text += '\n' + t('месяц не закончен')
  return text
}

/** Закономерность: доля месяцев с переработкой у пятой части сотрудников с наибольшими часами и у остальных. */
function findText(): string {
  const list = allRows.value
  const norm = data.value!.norm
  if (list.length < 5) return ''
  const overShare = (r: Row) => r.hours.slice(0, FULL).filter((h) => h > norm * 1.08).length / FULL
  const mean = (xs: number[]) => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : 0)
  const pct = (x: number) => Math.round(x * 100) + '%'
  const topCount = Math.max(1, Math.round(list.length / 5))
  const top = list.slice(0, topCount)
  const rest = list.slice(topCount)
  const avg = Math.round(mean(list.flatMap((r) => r.hours.slice(0, FULL))))
  return t(
    'У {top} сотрудников вверху списка переработка в {topShare} месяцев, у остальных - в {restShare}. ' +
      'Среднее по всем - {avg} ч в месяц при норме {norm} ч: среднее прячет перекос нагрузки между людьми.',
    {
      top: topCount,
      topShare: pct(mean(top.map(overShare))),
      restShare: pct(mean(rest.map(overShare))),
      avg,
      norm,
    },
  )
}

void load()
</script>

<style scoped lang="scss">
.overtime__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
}

.overtime__legend {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: $zs-text;
  font-size: 0.8rem;
}

.overtime__legend > i {
  width: 1.2rem;
  height: 0.8rem;
}

.overtime__ramp {
  display: inline-flex;

  i {
    width: 1.2rem;
    height: 0.8rem;
  }
}

// ячейка дашборда: строки делят высоту поровну, сколько бы сотрудников ни было
.overtime__grid {
  display: grid;
  flex: 1;
  grid-auto-rows: minmax(0, 1fr);
  grid-template-rows: auto;
  // ячейка дашборда: промежутки между квадратами по вертикали и горизонтали одинаковые
  gap: 0.1rem;
  min-height: 0;
}

// окно диаграммы: строки фиксированной высоты, при нехватке места прокручивается страница, шапка месяцев
// стоит на месте
.overtime--detailed .overtime__grid {
  align-content: start;
  grid-auto-rows: 1.1rem;
  row-gap: 0.1rem;
}

.overtime--detailed .overtime__month {
  position: sticky;
  top: 0;
  z-index: 1;
  background: $zs-bg-panel;
  // закрывает строки в отступе страницы над прилипшей шапкой
  box-shadow: 0 -0.5rem 0 $zs-bg-panel;
}

.overtime__month {
  overflow: hidden;
  align-self: end;
  padding-bottom: 0.2rem;
  color: $zs-text-muted;
  font-size: 0.7rem;
  text-align: center;
  white-space: nowrap;

  &--total {
    overflow: visible;
    margin-left: 1rem;
    text-align: left;
  }
}

.overtime__cell {
  min-height: 0;
}

.overtime__name {
  overflow: hidden;
  padding-right: 0.6rem;
  font-size: 0.8rem;
  line-height: 1.1rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overtime__total {
  position: relative;
  margin-left: 1rem;
}

.overtime__bar {
  position: absolute;
  top: 0.2rem;
  bottom: 0.2rem;
  left: 0;
  background: #9aa0a8;
}

// основание полосы — своё у каждой строки, чуть выше полосы: основания соседних строк не сливаются в линию
.overtime__axis {
  position: absolute;
  top: 0.1rem;
  bottom: 0.1rem;
  left: 0;
  border-left: 0.1rem solid $zs-text-muted;
}

// пунктир норматива заходит в промежуток между строками: линия идёт без разрывов
.overtime__norm {
  position: absolute;
  top: -0.1rem;
  bottom: 0;
  width: 0.1rem;
  // крупный пунктир; узор привязан к окну (fixed), поэтому штрихи соседних строк продолжают друг друга при любой
  // высоте строки
  background: repeating-linear-gradient(to bottom, $zs-text-muted 0 0.4rem, transparent 0.4rem 0.6rem) fixed;
}

.overtime__scale {
  position: relative;
  display: block;
  height: 1rem;
  margin-top: 1.4rem;
}

.overtime__tick {
  position: absolute;
  transform: translateX(-50%);
  font-variant-numeric: tabular-nums;

  &:first-child {
    transform: none;
  }
}

.overtime__norm-label {
  position: relative;
  height: 1.2rem;
  margin-left: 1rem;
  color: $zs-text-muted;
  font-size: 0.7rem;

  span {
    position: absolute;
    top: 0.2rem;
    transform: translateX(-50%);
    white-space: nowrap;
  }
}

// диаграммная палитра: синий — недогрузка, серый — норма, красный — переработка
.overtime--lnone {
  box-sizing: border-box;
  border: 0.1rem solid $zs-border;
  background: #fff;
}
.overtime--l-3 {
  background: #2c6bb3;
}
.overtime--l-2 {
  background: #7fa9d8;
}
.overtime--l-1 {
  background: #c6daef;
}
.overtime--l0 {
  background: #e4e5e8;
}
.overtime--l1 {
  background: #f2b8a8;
}
.overtime--l2 {
  background: #dd6b52;
}
.overtime--l3 {
  background: #a8281a;
}
</style>
