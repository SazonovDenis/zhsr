<template>
  <div class="date-calendar">
    <!-- общая шапка календаря и времени: подпись поля и «Очистить», год, дата со временем -->
    <div
      class="date-calendar__header q-pt-sm q-pb-sm q-pr-sm q-pl-md"
      :class="{ 'date-calendar__header--empty': day === null }"
    >
      <div class="row items-center justify-between no-wrap">
        <div class="date-calendar__header-text">{{ t(label) }}</div>
        <q-btn
          v-if="nullable && day"
          dense
          flat
          no-caps
          icon-right="close"
          color="grey-6"
          :label="t('Очистить')"
          @click="onDateUpdate(null)"
        />
        <!-- своя кнопка справа в шапке, например отмена уже сделанной отметки -->
        <slot name="header-action" />
      </div>
      <div class="date-calendar__header-year q-mt-xs">{{ formatYearHeader(day) }}</div>
      <div class="date-calendar__header-title">{{ headerTitle }}</div>
    </div>

    <div class="row date-calendar__panels" :class="{ 'no-wrap': !isCompact() }">
      <!-- собственную шапку календаря не показываем: шапка общая (minimal) -->
      <q-date
        class="custom-date relative-position"
        :class="{ 'custom-date-mobile': isCompact() }"
        :model-value="day"
        :mask="DATE_FORMAT"
        :no-unset="!nullable"
        minimal
        flat
        @update:model-value="onDateUpdate"
      />

      <!-- время — двумя циферблатами в цветах календаря: часы (0–11 снаружи, 12–23 внутри) и минуты -->
      <div v-if="withTime && !isMobile" class="time-grid" :class="{ 'time-grid--disabled': day === null }">
        <!-- два циферблата одного размера рядом: часы, справа минуты (с точностью до минуты) -->
        <div class="time-grid__body q-pa-sm">
          <div
            v-for="dial in TIME_DIALS"
            :key="dial.kind"
            class="time-dial"
            @pointerdown="onDialDown($event, dial.cells)"
            @pointermove="onDialMove($event, dial.cells)"
            @pointerup="onDialUp"
            @pointercancel="onDialUp"
          >
            <span v-if="hand(dial.kind)" class="time-dial__hand" :style="hand(dial.kind)!" />
            <span class="time-dial__center" />
            <button
              v-for="cell in dial.cells"
              :key="cell.value"
              type="button"
              class="time-dial__cell q-pa-none"
              :class="{
                'time-dial__cell--active': isTimeActive(cell),
                'time-dial__cell--inner': cell.inner,
              }"
              :style="cellStyle(cell)"
              :disabled="day === null"
              tabindex="-1"
            >
              {{ cell.label }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- на телефоне время — барабанами под календарём, по центру: пальцем на циферблате точно не попасть -->
    <TimeWheels
      v-if="withTime && isMobile"
      class="date-calendar__wheels"
      :model-value="time"
      :disabled="day === null"
      @update:model-value="onTimeUpdate"
    />
  </div>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed } from 'vue'

import { i18n, t } from '@/boot/i18n'
import { isMobile } from '@/shared/device'
import { screenScale } from '@/shared/screen'
import TimeWheels from './TimeWheels.vue'
import { DATE_FORMAT, joinIso, splitIso } from './dateValue'

/**
 * Календарь и время с общей шапкой: подпись label, «Очистить» (nullable), год и выбранная дата. Время (withTime) на
 * десктопе — циферблатами рядом с календарём, на телефоне — барабанами TimeWheels под ним. Значение — ISO, как у
 * DatePicker: дата «2026-10-30», с временем — «2026-10-30T21:00»; пусто — null. Выбор применяется сразу: кнопки «Ок»
 * и закрытия — у обёртки (окно DatePicker), здесь их нет.
 *
 *   <DateCalendar v-model="arrival" with-time label="Дата прибытия" :nullable="false" />
 */
const props = withDefaults(defineProps<{ withTime?: boolean; nullable?: boolean; label?: string }>(), {
  withTime: false,
  nullable: true,
  label: 'Дата',
})

const model = defineModel<string | null>({ default: null })

const day = computed(() => splitIso(model.value)[0])
const time = computed(() => splitIso(model.value)[1])

function isCompact(): boolean {
  return screenScale() < 3
}

function onDateUpdate(value: string | null) {
  // «Очистить» сбрасывает и время: время без даты не имеет смысла
  model.value = value === null ? null : joinIso(value, time.value ?? '00:00', props.withTime)
}

/** Деление циферблата: значение, подпись, позиция (0–11 по кругу, 0 — вверху) и кольцо. */
interface TimeCell {
  kind: 'hour' | 'minute'
  value: number
  label: string
  position: number
  inner: boolean
}

const pad2 = (n: number) => String(n).padStart(2, '0')

// часы: внешнее кольцо 0–11, внутреннее 12–23; минуты: 00–55 с шагом 5
const TIME_DIALS: { kind: 'hour' | 'minute'; cells: TimeCell[] }[] = [
  {
    kind: 'hour',
    cells: Array.from({ length: 24 }, (_, h) => ({
      kind: 'hour',
      value: h,
      label: String(h),
      position: h % 12,
      inner: h >= 12,
    })),
  },
  {
    kind: 'minute',
    cells: Array.from({ length: 12 }, (_, i) => ({
      kind: 'minute',
      value: i * 5,
      label: pad2(i * 5),
      position: i,
      inner: false,
    })),
  },
]

// радиус колец — доля радиуса циферблата
const OUTER_RADIUS = 0.8
const INNER_RADIUS = 0.52

function radiusOf(cell: TimeCell): number {
  return cell.inner ? INNER_RADIUS : OUTER_RADIUS
}

/** Положение деления на круге: центр циферблата — 50%, 0 — вверху, по часовой стрелке. */
function cellStyle(cell: TimeCell): Record<string, string> {
  const angle = (cell.position * Math.PI) / 6
  const r = radiusOf(cell) * 50
  return { left: `${50 + r * Math.sin(angle)}%`, top: `${50 - r * Math.cos(angle)}%` }
}

/**
 * Стрелка от центра к выбранному значению: у часов — к выбранному делению (с учётом кольца), у минут — к точной
 * минуте (6° на минуту), даже если она не кратна пяти и деления для неё нет.
 */
function hand(kind: 'hour' | 'minute'): Record<string, string> | null {
  if (!time.value) return null
  const [h, m] = time.value.split(':').map(Number)
  if (kind === 'minute') return { height: `${OUTER_RADIUS * 50}%`, transform: `rotate(${(m ?? 0) * 6}deg)` }
  const cell = TIME_DIALS[0]!.cells.find((c) => c.value === h)
  return cell ? { height: `${radiusOf(cell) * 50}%`, transform: `rotate(${cell.position * 30}deg)` } : null
}

/**
 * Деление под указателем: по углу от центра — ближайшая позиция, по расстоянию от центра — кольцо (у часов: ближе к
 * центру — внутреннее, 12–23). Для минут — ближайшая с шагом 5.
 */
function cellAt(e: PointerEvent, cells: TimeCell[]): TimeCell | undefined {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const x = e.clientX - (rect.left + rect.width / 2)
  const y = e.clientY - (rect.top + rect.height / 2)
  const angle = (Math.atan2(x, -y) * 180) / Math.PI
  const position = Math.round((((angle % 360) + 360) % 360) / 30) % 12
  const distance = Math.hypot(x, y) / (rect.width / 2)
  const inner = distance < (OUTER_RADIUS + INNER_RADIUS) / 2
  return cells.find((c) => c.position === position && c.inner === inner) ?? cells.find((c) => c.position === position)
}

// перетаскивание по циферблату: нажали — выбирается деление под указателем, двигают с нажатой кнопкой — оно меняется
let dragging = false

function onDialDown(e: PointerEvent, cells: TimeCell[]) {
  if (day.value === null) return
  dragging = true
  // указатель захвачен: перетаскивание продолжается и за пределами циферблата
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  pick(e, cells)
}

function onDialMove(e: PointerEvent, cells: TimeCell[]) {
  if (dragging) pick(e, cells)
}

function onDialUp() {
  dragging = false
}

function pick(e: PointerEvent, cells: TimeCell[]) {
  // минуты — с точностью до одной: ближайшая минута по углу
  if (cells[0]?.kind === 'minute') {
    const minute = minuteAt(e)
    const [h] = (time.value ?? '00:00').split(':')
    const value = `${h}:${pad2(minute)}`
    if (value !== time.value) onTimeUpdate(value)
    return
  }
  const cell = cellAt(e, cells)
  if (cell && !isTimeActive(cell)) onTimeCell(cell)
}

/** Минута под указателем: по углу от центра, 6° на минуту. */
function minuteAt(e: PointerEvent): number {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const x = e.clientX - (rect.left + rect.width / 2)
  const y = e.clientY - (rect.top + rect.height / 2)
  const angle = ((((Math.atan2(x, -y) * 180) / Math.PI) % 360) + 360) % 360
  return Math.round(angle / 6) % 60
}

function isTimeActive(cell: TimeCell): boolean {
  if (!time.value) return false
  const [h, m] = time.value.split(':').map(Number)
  return cell.kind === 'hour' ? cell.value === h : cell.value === m
}

/** Час меняет только часы, минута — только минуты; второе берётся из текущего времени (или 00). */
function onTimeCell(cell: TimeCell) {
  const [h, m] = (time.value ?? '00:00').split(':')
  onTimeUpdate(cell.kind === 'hour' ? `${pad2(cell.value)}:${m}` : `${h}:${pad2(cell.value)}`)
}

function onTimeUpdate(value: string | null) {
  model.value = joinIso(day.value, value, props.withTime)
}

function formatYearHeader(day: string | null): string {
  if (day === null) return ' '
  return String(date.extractDate(day, DATE_FORMAT).getFullYear())
}

/** Заголовок общей шапки: «1 ноября, воскресенье», со временем — «1 ноября, воскресенье, 23:09». */
const headerTitle = computed(() => {
  const title = formatDateHeader(day.value)
  return props.withTime && day.value && time.value ? `${title}, ${time.value}` : title
})

function formatDateHeader(day: string | null): string {
  if (day === null) return t('не выбрана')
  const formatter = new Intl.DateTimeFormat(i18n.global.locale.value, {
    day: 'numeric',
    month: 'long',
    weekday: 'long',
  })
  const parts = formatter.formatToParts(date.extractDate(day, DATE_FORMAT))
  const part = (type: string) => parts.find((p) => p.type === type)?.value
  return `${part('day')} ${part('month')}, ${part('weekday')}`
}
</script>

<style scoped lang="scss">
.date-calendar {
  display: flex;
  flex-direction: column;
}

// общая шапка календаря и времени — в цветах шапки календаря Quasar
.date-calendar__header {
  border-radius: 0.3rem 0.3rem 0 0;
  background: $zs-bg-header;
}

.date-calendar__header-text {
  color: $zs-text;
  font-size: 0.9rem;
  font-weight: 600;
}

.date-calendar__header-year {
  color: $primary;
  font-size: 0.9rem;
}

.date-calendar__header-title {
  color: $primary;
  font-size: 1.2rem;

  .date-calendar__header--empty & {
    color: $zs-text-faint;
  }
}

.date-calendar__panels {
  gap: 1rem;
}

// Время — в цветах календаря (q-date): шапка той же высоты и того же фона, выбранное — синей заливкой
.time-grid {
  display: flex;
  flex-direction: column;
  min-width: 9rem;

  &--disabled {
    opacity: 0.5;
  }
}

// циферблаты рядом: часы, справа минуты; на узком экране, где рядом не помещаются, — друг под другом
.time-grid__body {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 1rem;
}

.time-dial {
  position: relative;
  // перетаскивание пальцем не прокручивает страницу
  touch-action: none;
  user-select: none;
  cursor: pointer;
  width: 10rem;
  height: 10rem;
  border-radius: 50%;
  background: $zs-bg-header;
}

.time-dial__center {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0.3rem;
  height: 0.3rem;
  border-radius: 50%;
  background: $primary;
  transform: translate(-50%, -50%);
}

// стрелка растёт от центра вверх и поворачивается к выбранному делению
.time-dial__hand {
  position: absolute;
  bottom: 50%;
  left: calc(50% - 0.1rem);
  width: 0.1rem;
  background: $primary;
  transform-origin: bottom center;

  // конец стрелки — точка: видна, когда минута не кратна пяти и подсвеченного деления нет
  &::after {
    content: '';
    position: absolute;
    top: -0.2rem;
    left: -0.2rem;
    width: 0.5rem;
    height: 0.5rem;
    border-radius: 50%;
    background: $primary;
  }
}

.time-dial__cell {
  position: absolute;
  width: 1.8rem;
  height: 1.8rem;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: $zs-text;
  font: inherit;
  font-size: 0.9rem;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  transform: translate(-50%, -50%);
  cursor: pointer;

  &--inner {
    color: $zs-text-muted;
    font-size: 0.8rem;
  }

  &:hover:not(:disabled):not(.time-dial__cell--active) {
    background: rgba($primary, 0.1);
  }

  // выбранное — светлым кружком, как выбранный день календаря
  &--active {
    background: $zs-selected-bg;
    box-shadow: inset 0 0 0 0.0625rem $zs-selected-border;
    color: $zs-selected-fg;
  }

  &:disabled {
    cursor: default;
  }
}

.custom-date-mobile {
  width: auto;
  min-width: 18rem;
}

.date-calendar__wheels {
  align-self: center;
  // вплотную к календарю: снизу у календаря Quasar свой отступ 1rem
  margin-top: -1rem;
}
</style>
