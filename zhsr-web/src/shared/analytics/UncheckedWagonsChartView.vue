<template>
  <ChartView
    :chart="view"
    :empty="data?.length ? '' : t('Вагонов в эксплуатации нет')"
    class="unchecked"
    :class="{ 'unchecked--detailed': detailed }"
  >
    <template v-if="detailed" #tools>
      <div class="q-mb-md">
        <div class="unchecked__tools">
          <DatePicker
            :model-value="atDay"
            label="По состоянию на:"
            date-text="По состоянию на"
            :nullable="false"
            :quick="DAYS"
            :presets="monthEnds"
            :short-count="3"
            @update:model-value="setDay"
          />
          <CbSegment v-model="rides" single required :values="RIDES_ITEMS" />
        </div>
        <div v-if="future" class="unchecked__future q-mt-xs">{{ t('Дата не может быть в будущем') }}</div>
      </div>
    </template>

    <template #default>
      <div v-if="detailed" class="unchecked__legend q-mb-md">
        <span v-for="band in LEGEND" :key="band.id" class="unchecked__legend-item">
          <i class="unchecked__swatch" :class="'unchecked--b' + band.id" />{{ t(band.text) }}
        </span>
        <span class="unchecked__legend-item">
          <i class="unchecked__square unchecked__square--rides unchecked__square--key" />{{ t(RIDES_TEXT) }}
        </span>
        <span class="unchecked__legend-item">
          <i class="unchecked__square unchecked__square--key" />{{ t(STANDS_TEXT) }}
        </span>
      </div>

      <div class="unchecked__charts">
        <section v-for="chart in charts" :key="chart.id">
          <div v-if="detailed" class="unchecked__title q-mb-sm">{{ chart.name }}</div>
          <div class="unchecked__lines">
            <template v-for="line in chart.lines" :key="line.title">
              <span class="unchecked__name">{{ t(line.title) }}</span>
              <span class="unchecked__squares">
                <component
                  :is="linked ? RouterLink : 'i'"
                  v-for="cell in line.cells"
                  :key="cell.row.wagon.id"
                  :to="linked ? listLocation(cell, line.route) : undefined"
                  class="unchecked__square"
                  :class="['unchecked--b' + cell.band, { 'unchecked__square--rides': cell.rides }]"
                  :title="cellTitle(cell)"
                />
              </span>
            </template>
          </div>
        </section>
      </div>
    </template>
  </ChartView>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import type { UncheckedWagonRow } from '@/api/analytics'
import { fetchUncheckedWagons } from '@/api/analytics'
import { t } from '@/boot/i18n'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import type { DatePreset, QuickDate } from '@/shared/components/period/quickDates'
import { isMobile } from '@/shared/device'
import { dictManager } from '@/shared/dict/DictManager'
import { isoDateTime } from '@/shared/format'
import ChartView from './ChartView.vue'
import { useChart } from './useChart'

/**
 * Диаграмма «Вагоны без проверки»: две линии фигур — по чек-листам вагона и по описям. Фигура — вагон,
 * цвет — сколько суток назад проверяли, круг — вагон был в рейсе за последние 14 суток, квадрат — не был. Внутри линии — от давно
 * не проверенных к недавно проверенным.
 *
 * detailed = false — ячейка дашборда: две линии по всем депо. detailed = true — окно раскрытой диаграммы:
 * дата, на которую показано состояние, отбор по рейсам, легенда, диаграмма по всем депо и на каждое депо, подсказка по
 * фигуре, по клику — список чек-листов или описей вагона за год. Закономерность с цифрами — по всем депо — уходит владельцу
 * событием find.
 */
const props = defineProps<{ detailed: boolean }>()
const emit = defineEmits<{ find: [text: string] }>()

/** Давность проверки: max — верхняя граница в сутках; у последней — проверок не было. */
const BANDS = [
  { id: 0, text: 'до 7 суток без проверки', max: 7 },
  { id: 1, text: '8-30 суток без проверки', max: 30 },
  { id: 2, text: '31-90 суток без проверки', max: 90 },
  { id: 3, text: 'больше 90 суток без проверки', max: Infinity },
  { id: 4, text: 'не проверяли ни разу', max: null },
]
const NEVER = 4
/** Легенда — от худших к лучшим. */
const LEGEND = [...BANDS].reverse()
const RIDES_TEXT = 'вагон был в рейсе за последние 14 суток'
const STANDS_TEXT = 'рейсов за 14 суток не было'
const KINDS = [
  { title: 'Чек-лист вагона', field: 'lastCheckDt', route: 'checks' },
  { title: 'Опись экипировки', field: 'lastEquipDt', route: 'equip' },
] as const
/** Проверка свежая, если она не старше стольких суток. */
const FRESH_DAYS = 30
/** Вагон ходит в рейсы, если отправлялся не раньше стольких суток назад. */
const TRIP_DAYS = 14
/** Отбор вагонов: был в рейсе, не был, все. */
const RIDES_YES = 1
const RIDES_NO = 2
const RIDES_ALL = 3
const RIDES_ITEMS = [
  { id: RIDES_YES, text: 'Был в рейсе' },
  { id: RIDES_NO, text: 'Не был в рейсе' },
  { id: RIDES_ALL, text: 'Все' },
]
/** Кнопки готовых дат в рамке: сейчас, конец прошлого и позапрошлого месяца. */
const DAYS: QuickDate[] = ['now', 'prevMonthEnd', 'prevPrevMonthEnd']
/** Концы месяцев в списке окна даты: подписи по номеру месяца. */
const MONTH_END_TEXTS = [
  'на конец января',
  'на конец февраля',
  'на конец марта',
  'на конец апреля',
  'на конец мая',
  'на конец июня',
  'на конец июля',
  'на конец августа',
  'на конец сентября',
  'на конец октября',
  'на конец ноября',
  'на конец декабря',
]
/** Сколько прошлых месяцев в списке окна даты. */
const MONTH_ENDS = 12
/** Группа вагонов без депо приписки. */
const NO_DEPOT = -1

interface Cell {
  row: UncheckedWagonRow
  /** полных суток с последней проверки; null — проверок не было */
  days: number | null
  band: number
  stale: boolean
  /** отправлялся в рейс за последние 14 суток */
  rides: boolean
}

const view = useChart(
  fetchRows,
  (text) => emit('find', text),
  (rows) => {
    at.value = requestedAt
    return findText(rows)
  },
)
const { data, load } = view

/** Момент, по состоянию на который показана диаграмма: от него считается давность. */
const at = ref(new Date())
// момент последнего запроса: at сменится вместе с данными — до ответа показана прежняя диаграмма
let requestedAt = at.value

/** Фигуры — ссылки на списки десктопа; на телефоне таких списков нет. */
const linked = computed(() => props.detailed && !isMobile.value)

const route = useRoute()
const router = useRouter()

function isoDay(d: Date): string {
  return date.formatDate(d, 'YYYY-MM-DD')
}

/**
 * День, на который показано состояние. Хранится в адресе (?at=), чтобы возврат из списка показал тот же день; без него —
 * сегодня.
 */
const queryDay = route.query.at
const atDay = ref(
  props.detailed && typeof queryDay === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(queryDay)
    ? queryDay
    : isoDay(new Date()),
)
const future = computed(() => atDay.value > isoDay(new Date()))

/** Сегодня — текущий момент, другой день — его конец. */
function dayMoment(day: string): Date {
  if (day === isoDay(new Date())) return new Date()
  const [y, m, d] = day.split('-').map(Number)
  return new Date(y!, m! - 1, d, 23, 59, 59)
}

/** Список окна даты: сейчас и концы прошлых месяцев; год — если не текущий. */
const monthEnds = computed<DatePreset[]>(() => {
  const now = new Date()
  const ends = Array.from({ length: MONTH_ENDS }, (_, i) => new Date(now.getFullYear(), now.getMonth() - i, 0))
  return [
    { value: isoDay(now), label: t('Сейчас') },
    ...ends.map((d) => ({
      value: isoDay(d),
      label: t(MONTH_END_TEXTS[d.getMonth()]!) + (d.getFullYear() === now.getFullYear() ? '' : ` ${d.getFullYear()}`),
    })),
  ]
})

function setDay(day: string | null) {
  if (!day || day === atDay.value) return
  atDay.value = day
  if (future.value) return
  void router.replace({ query: { ...route.query, at: day === isoDay(new Date()) ? undefined : day } })
  void load()
}

function dateText(d: Date): string {
  return date.formatDate(d, 'DD.MM.YYYY')
}

/** Переход в список чек-листов или описей: вагон и год до показанной даты. */
function listLocation(cell: Cell, name: string) {
  return {
    name,
    query: {
      query: cell.row.wagon.number,
      from: date.formatDate(date.subtractFromDate(at.value, { years: 1 }), 'YYYY-MM-DD'),
      to: date.formatDate(at.value, 'YYYY-MM-DD'),
    },
  }
}

function daysSince(dt: string): number {
  return (at.value.getTime() - new Date(dt).getTime()) / 86400000
}

function toCell(row: UncheckedWagonRow, dt: string | null): Cell {
  const days = dt === null ? null : Math.max(0, Math.floor(daysSince(dt)))
  const band = days === null ? NEVER : BANDS.findIndex((b) => b.max !== null && days <= b.max)
  const stale = days === null || days > FRESH_DAYS
  const rides = row.lastTripDt !== null && daysSince(row.lastTripDt) <= TRIP_DAYS
  return { row, days, band, stale, rides }
}

const depots = computed(() =>
  [...new Set((data.value ?? []).map((r) => r.depot ?? NO_DEPOT))]
    .map((id) => ({ id, text: id === NO_DEPOT ? t('Депо не задано') : dictManager.getValue('Depot', id) }))
    .sort((a, b) => a.text.localeCompare(b.text)),
)

/** Линии мозаики: вагоны по чек-листам и по описям, от давно не проверенных к недавно проверенным. */
function toLines(rows: UncheckedWagonRow[]) {
  return KINDS.map((kind) => ({
    title: kind.title,
    route: kind.route,
    cells: rows
      .map((r) => toCell(r, r[kind.field]))
      .filter(ridesMatch)
      .sort((a, b) => (a.days === b.days ? 0 : (b.days ?? Infinity) - (a.days ?? Infinity))),
  }))
}

/** Отбор по рейсам; хранится в адресе (?rides=), чтобы возврат из списка показал тот же отбор. */
const rides = ref(RIDES_ITEMS.find((x) => x.id === Number(route.query.rides) && props.detailed)?.id ?? RIDES_ALL)
watch(rides, (id) => {
  void router.replace({ query: { ...route.query, rides: id === RIDES_ALL ? undefined : String(id) } })
})

function ridesMatch(cell: Cell): boolean {
  return rides.value === RIDES_ALL || cell.rides === (rides.value === RIDES_YES)
}

/** Доля вагонов без проверки дольше 30 суток: по ней депо идут от худших к лучшим. */
function staleShare(lines: ReturnType<typeof toLines>): number {
  const cells = lines.flatMap((l) => l.cells)
  return cells.filter((c) => c.stale).length / cells.length
}

/**
 * Ячейка дашборда — одна диаграмма по всем депо; раскрытая — по всем депо, затем по диаграмме на каждое депо, от худших
 * к лучшим.
 */
const charts = computed(() => {
  const rows = data.value ?? []
  if (!props.detailed) return [{ id: 0, name: '', lines: toLines(rows) }]
  const byDepot = depots.value
    .map((d) => ({
      id: d.id,
      name: d.text,
      lines: toLines(rows.filter((r) => (r.depot ?? NO_DEPOT) === d.id)),
    }))
    // депо без вагонов по отбору не показывается
    .filter((d) => d.lines.some((l) => l.cells.length > 0))
    .sort((a, b) => staleShare(b.lines) - staleShare(a.lines))
  return [{ id: 0, name: t('Все депо'), lines: toLines(rows) }, ...byDepot]
})

function cellTitle(cell: Cell): string {
  const { wagon, lastTripDt } = cell.row
  const lines = [
    t('Вагон {number} • {type}', { number: wagon.number, type: dictManager.getValue('WagonType', wagon.wagonType) }),
    cell.days === null ? t('Последняя проверка: не было') : t('Последняя проверка: {n} сут назад', { n: cell.days }),
  ]
  if (lastTripDt !== null) {
    lines.push(t('Последний рейс: {date}', { date: dateText(new Date(lastTripDt)) }))
  }
  lines.push(t(cell.rides ? RIDES_TEXT : STANDS_TEXT))
  return lines.join('\n')
}

/** Закономерность: вагоны, которые ходят в рейсы без свежей проверки, и ни разу не проверенные — по каждой мозаике. */
function findText(rows: UncheckedWagonRow[]): string {
  if (!rows.length) return ''
  return KINDS.map((kind) => {
    const cells = rows.map((r) => toCell(r, r[kind.field]))
    return t(
      '{name}: ходят в рейсы без проверки дольше 30 суток - вагонов {blind}; не проверяли ни разу - вагонов {never}.',
      {
        name: t(kind.title),
        blind: cells.filter((c) => c.rides && c.stale).length,
        never: cells.filter((c) => c.days === null).length,
      },
    )
  }).join(' ')
}

async function fetchRows(): Promise<UncheckedWagonRow[]> {
  requestedAt = dayMoment(atDay.value)
  const [rows] = await Promise.all([
    fetchUncheckedWagons(isoDateTime(requestedAt)),
    dictManager.prepareDict('Depot'),
    dictManager.prepareDict('WagonType'),
  ])
  return rows
}
if (future.value) atDay.value = isoDay(new Date())
void load()
</script>

<style scoped lang="scss">
.unchecked {
  --square: 1.1rem;

  &--detailed {
    --square: 1.3rem;
  }
}

.unchecked__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
}

.unchecked__future {
  color: $zs-danger;
  font-size: 0.8rem;
}

.unchecked__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1rem;
  color: $zs-text;
  font-size: 0.8rem;
}

.unchecked__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;

  .unchecked__square {
    width: 0.9rem;
    height: 0.9rem;
  }
}

// цвет давности в легенде: полоска, форма не значит «ездит» или «стоит»
.unchecked__swatch {
  width: 1.4rem;
  height: 0.6rem;
  border-radius: 0.2rem;
}

.unchecked__charts {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 1rem;
  min-height: 0;
}

.unchecked--detailed .unchecked__charts {
  gap: 2rem;
}

.unchecked__title {
  font-size: 1rem;
  font-weight: 600;
}

// ячейка дашборда: подпись над линией; раскрытая диаграмма: подпись слева от линии
.unchecked__lines {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0.4rem;
}

.unchecked--detailed .unchecked__lines {
  grid-template-columns: 10rem minmax(0, 1fr);
  gap: 0.6rem;
}

.unchecked__name {
  color: $zs-text-muted;
  font-size: 0.8rem;
  line-height: var(--square);
}

.unchecked__squares {
  display: flex;
  flex-wrap: wrap;
  gap: 0.2rem;
}

.unchecked__square {
  flex: none;
  box-sizing: border-box;
  width: var(--square);
  height: var(--square);
  border-radius: 0.2rem;

  // вагон ездит — круг
  &--rides {
    border-radius: 50%;
  }

  // форма в легенде, без цвета давности
  &--key {
    background: $zs-text-faint;
  }
}

// давность проверки: от зелёного (свежая) через жёлтый к красному (проверок не было)
.unchecked--b0 {
  background: #2e9d6a;
}
.unchecked--b1 {
  background: #a5d065;
}
.unchecked--b2 {
  background: #f7d046;
}
.unchecked--b3 {
  background: #ef8a3c;
}
.unchecked--b4 {
  background: #c62828;
}
</style>
