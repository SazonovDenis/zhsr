<template>
  <div ref="scrollEl" class="data-table">
    <div class="data-table__inner">
      <!-- шапка: в том же контейнере прокрутки, что и строки, поэтому колонки не съезжают из-за полосы прокрутки -->
      <div class="data-table__head data-table__grid" :style="gridStyle" role="row">
        <div
          v-for="col in columns"
          :key="col.field"
          class="data-table__head-cell"
          :class="[alignClass(col), { 'data-table__head-cell--sortable': col.sortable }]"
          role="columnheader"
          :aria-sort="ariaSort(col)"
          :tabindex="col.sortable ? 0 : undefined"
          :title="col.sortable ? t('Сортировать') : undefined"
          @click="onHeaderClick(col)"
          @keydown.enter.prevent="onHeaderClick(col)"
          @keydown.space.prevent="onHeaderClick(col)"
        >
          <component :is="col.header" v-if="col.header" :column="col" />
          <template v-else>{{ headerText(col) }}</template>
          <q-icon
            v-if="col.sortable"
            :name="sortIcon(col)"
            size="0.9rem"
            class="data-table__sort-icon"
            :class="{ 'data-table__sort-icon--active': sortedBy(col) }"
          />
        </div>
      </div>

      <!-- строки рендерятся по мере прокрутки: в DOM только видимые -->
      <q-virtual-scroll
        v-if="scrollEl && rows.length > 0"
        ref="virtualScroll"
        :scroll-target="scrollEl"
        :items="rows"
        role="rowgroup"
      >
        <template #default="{ item, index }: { item: Row; index: number }">
          <div
            :key="keyOf(item)"
            class="data-table__row"
            :class="{ 'data-table__row--selected': isSelected(item), 'data-table__row--struck': struck?.(item) }"
            role="row"
            @click="onRowClick(item)"
            @dblclick="emit('row-dblclick', item)"
          >
            <slot name="row" :row="item" :index="index" :grid-style="gridStyle">
              <div class="data-table__grid" :style="gridStyle">
                <div
                  v-for="col in columns"
                  :key="col.field"
                  class="data-table__cell"
                  :class="[alignClass(col), col.cellClass]"
                  role="cell"
                >
                  <component :is="col.cell ?? CellText" :row="item" :value="item[col.field]" :column="col" />
                </div>
              </div>
            </slot>
          </div>
        </template>
      </q-virtual-scroll>

      <!-- строк ещё нет и идёт загрузка — спиннер на месте строк: у пустой таблицы высота — только шапка -->
      <LoadSpinner v-if="loading && rows.length === 0" />
      <!-- ошибка загрузки — на месте строк, а не «нет данных» -->
      <ErrorBox v-else-if="error && !loading" :error="error" :retry="retry" class="data-table__error" />
      <div v-else-if="rows.length === 0 && !loading" class="data-table__empty">{{ emptyText }}</div>

      <!-- подвал по колонкам: есть, если подвал задан хотя бы у одной колонки -->
      <div v-if="hasColumnFooter" class="data-table__foot data-table__grid" :style="gridStyle" role="row">
        <div v-for="col in columns" :key="col.field" class="data-table__foot-cell" :class="alignClass(col)">
          <template v-if="typeof col.footer === 'string'">{{ col.footer }}</template>
          <component :is="col.footer" v-else-if="col.footer" :rows="rows" :column="col" />
        </div>
      </div>
    </div>

    <!-- свободный подвал под таблицей -->
    <div v-if="$slots.footer" class="data-table__footer">
      <slot name="footer" :rows="rows" />
    </div>

    <!-- строки уже есть, идёт новая загрузка — спиннер поверх строк, без плавного появления -->
    <q-inner-loading :showing="loading && rows.length > 0" :transition-duration="0">
      <q-spinner color="primary" size="2.5rem" />
    </q-inner-loading>
  </div>
</template>

<script setup lang="ts" generic="Row extends object">
import type { QVirtualScroll } from 'quasar'
import { computed, nextTick, ref, watch } from 'vue'

import { t } from '@/boot/i18n'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import type { ShownError } from '@/shared/errors'
import CellText from './CellText.vue'
import type { Column, RowKey, TableSort } from './types'

/**
 * Таблица из колонок-описаний (см. Column в types.ts).
 *
 * - Раскладка на CSS grid: ширина колонки — дорожка grid, поэтому ширина в символах ('10ch') соблюдается точно.
 * - Ячейка — компонент из column.cell, по умолчанию CellText. Строку целиком можно заменить слотом row.
 * - Шапка и подвал по колонкам закреплены сверху и снизу при прокрутке, строки рендерятся виртуально.
 * - Сортировка — только по колонкам с sortable. Таблица сама не сортирует: клик по заголовку (или Enter на нём)
 *   отправляет событие sort с полным запросом, а владелец загружает данные заново. Клики по одной колонке идут по
 *   кругу: по возрастанию → по убыванию → без сортировки (null, порядок сервера). Клик по другой колонке —
 *   сортировка по ней по возрастанию.
 * - Выбранная строка (v-model:selected) подсвечивается, а при смене выбора прокручивается в видимую область.
 * - Строка может быть зачёркнута (struck): значения перечёркнуты, например у отключённой записи.
 * - Подвал: строка по колонкам (column.footer) и свободный слот footer под таблицей.
 */
const props = withDefaults(
  defineProps<{
    columns: Column<Row>[]
    rows: Row[]
    /** поле строки с ключом или функция, вычисляющая ключ */
    // eslint-disable-next-line @typescript-eslint/no-redundant-type-constituents -- ложное срабатывание на generic компонента
    rowKey: (keyof Row & string) | ((row: Row) => RowKey)
    /** ключ выбранной строки */
    selected?: RowKey | null
    /** строка показывается зачёркнутой: например, отключённая запись; null — зачёркнутых строк нет */
    struck?: ((row: Row) => boolean) | null
    sort?: TableSort | null
    loading?: boolean
    /** текст на русском, когда строк нет; переводится при показе */
    empty?: string
    /** ошибка загрузки строк: показывается вместо строк; null — ошибки нет */
    error?: ShownError | null
    /** повтор загрузки: клик по ошибке запускает его; null — повтора нет */
    retry?: (() => void) | null
  }>(),
  { selected: null, struck: null, sort: null, loading: false, empty: 'Нет данных', error: null, retry: null },
)

const emit = defineEmits<{
  'update:selected': [key: RowKey | null]
  'row-click': [row: Row]
  'row-dblclick': [row: Row]
  sort: [sort: TableSort | null]
}>()

defineSlots<{
  row?: (props: { row: Row; index: number; gridStyle: Record<string, string> }) => unknown
  footer?: (props: { rows: Row[] }) => unknown
}>()

const scrollEl = ref<HTMLElement | null>(null)
const virtualScroll = ref<QVirtualScroll | null>(null)

const gridStyle = computed(() => ({
  gridTemplateColumns: props.columns.map((col) => col.width ?? 'minmax(min-content, 1fr)').join(' '),
}))

const hasColumnFooter = computed(() => props.columns.some((col) => col.footer !== undefined))

const emptyText = computed(() => t(props.empty))

function keyOf(row: Row): RowKey {
  const rowKey = props.rowKey
  if (typeof rowKey === 'function') return rowKey(row)
  const value: unknown = row[rowKey]
  return value as RowKey
}

function isSelected(row: Row): boolean {
  return props.selected !== null && keyOf(row) === props.selected
}

function alignClass(col: Column<Row>): string {
  return `data-table--align-${col.align ?? 'left'}`
}

function headerText(col: Column<Row>): string {
  const title = t(col.title)
  return col.unit ? `${title}, ${t(col.unit)}` : title
}

function sortFieldOf(col: Column<Row>): string {
  return col.sortField ?? col.field
}

function sortedBy(col: Column<Row>): boolean {
  return !!col.sortable && props.sort?.field === sortFieldOf(col)
}

// стрелка вниз — по возрастанию (как читается список сверху вниз), вверх — по убыванию; не отсортирована — обе
function sortIcon(col: Column<Row>): string {
  if (!sortedBy(col)) return 'unfold_more'
  return props.sort?.desc ? 'arrow_upward' : 'arrow_downward'
}

function ariaSort(col: Column<Row>): 'ascending' | 'descending' | 'none' | undefined {
  if (!col.sortable) return undefined
  if (!sortedBy(col)) return 'none'
  return props.sort?.desc ? 'descending' : 'ascending'
}

function onHeaderClick(col: Column<Row>) {
  if (!col.sortable) return
  const field = sortFieldOf(col)
  if (props.sort?.field !== field) emit('sort', { field, desc: false })
  else if (!props.sort.desc) emit('sort', { field, desc: true })
  else emit('sort', null)
}

function onRowClick(row: Row) {
  emit('update:selected', keyOf(row))
  emit('row-click', row)
}

// выбранная строка могла оказаться за пределами видимой области (выбор пришёл снаружи) — прокручиваем к ней
watch(
  () => props.selected,
  async (key) => {
    if (key === null) return
    const index = props.rows.findIndex((row) => keyOf(row) === key)
    if (index < 0) return
    await nextTick()
    virtualScroll.value?.scrollTo(index)
  },
)
</script>

<style scoped lang="scss">
.data-table {
  position: relative;
  flex: 0 1 auto;
  min-height: 0;
  overflow: auto;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
  background: #fff;
  font-size: 0.84375rem;
}

.data-table__inner {
  min-width: fit-content;
}

.data-table__grid {
  display: grid;
}

.data-table__head {
  position: sticky;
  top: 0;
  z-index: 1;
  background: $zs-table-head-bg;
  box-shadow: inset 0 -0.0625rem 0 $zs-table-head-border;
}

.data-table__head-cell {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.5625rem 0.875rem;
  color: $zs-table-head-fg;
  font-size: 0.8125rem;
  font-weight: 600;
  white-space: nowrap;

  &--sortable {
    cursor: pointer;
    user-select: none;

    // под курсором подсвечиваются значки сортировки
    &:hover .data-table__sort-icon:not(.data-table__sort-icon--active) {
      color: $zs-table-head-icon-hover;
      opacity: 1;
    }
  }
}

.data-table__sort-icon {
  color: $zs-table-head-icon;
  opacity: 0.5;

  &--active {
    color: $zs-table-head-icon-active;
    opacity: 1;
  }
}

.data-table__row {
  cursor: pointer;

  &:hover {
    background: $zs-row-hover-bg;
  }

  &--selected,
  &--selected:hover {
    background: $zs-row-selected-bg;
    box-shadow: inset 0.125rem 0 0 $zs-row-selected-bar;
  }
}

.data-table__cell {
  min-width: 0;
  padding: 0.625rem 0.875rem;
  border-bottom: 0.0625rem solid $zs-border-row;
}

.data-table__row--struck .data-table__cell {
  text-decoration: line-through;
}

.data-table__foot {
  position: sticky;
  bottom: 0;
  z-index: 1;
  background: $zs-bg-header;
  box-shadow: inset 0 0.0625rem 0 $zs-border;
  font-weight: 600;
}

.data-table__foot-cell {
  padding: 0.5625rem 0.875rem;
}

.data-table__footer {
  padding: 0.5625rem 0.875rem;
  border-top: 0.0625rem solid $zs-border;
}

.data-table__empty {
  padding: 2rem;
  color: $zs-text-muted;
  text-align: center;
}

.data-table__error {
  margin: 1rem;
}

.data-table--align-left {
  justify-content: flex-start;
  text-align: left;
}

.data-table--align-center {
  justify-content: center;
  text-align: center;
}

.data-table--align-right {
  justify-content: flex-end;
  text-align: right;
}
</style>
