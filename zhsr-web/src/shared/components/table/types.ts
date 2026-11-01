import type { Component } from 'vue'

/** Ключ строки таблицы. */
export type RowKey = string | number

/**
 * Описание колонки таблицы DataTable.
 *
 * Колонка — это данные, а не разметка: описания колонок страницы лежат в отдельном файле (например,
 * wagonsColumns.ts). Сложная ячейка — отдельный компонент, указанный в cell; он получает props CellProps.
 * Состояние страницы в описание колонки не пишется: ячейка получает его через provide/inject.
 */
export interface Column<Row> {
  /** поле строки, значение которого показывает колонка; оно же ключ колонки */
  field: keyof Row & string
  /** заголовок на русском; переводится при показе */
  title: string
  /** единица измерения на русском: показывается в заголовке после запятой, переводится при показе */
  unit?: string
  /**
   * ширина колонки — размер дорожки CSS grid: '10ch' (в символах), '12rem', '1fr', 'minmax(10rem, 1fr)';
   * по умолчанию 'minmax(min-content, 1fr)'
   */
  width?: string
  align?: 'left' | 'center' | 'right'
  /** CSS-класс ячейки, например zs-mono для номеров */
  cellClass?: string
  /** значение строкой для простой ячейки без компонента */
  format?: (value: Row[keyof Row], row: Row) => string
  /** компонент ячейки; получает props CellProps */
  cell?: Component
  /** компонент заголовка вместо title; получает props HeaderProps */
  header?: Component
  /**
   * подвал колонки: текст или компонент (получает props FooterProps); если подвал есть хотя бы у одной колонки,
   * таблица показывает строку подвала, выровненную по колонкам
   */
  footer?: string | Component
  /**
   * по колонке можно сортировать: клик по заголовку отправляет запрос сортировки. Сортировка доступна только
   * колонкам с этим признаком
   */
  sortable?: boolean
  /** поле, по которому сортирует сервер, если оно не совпадает с field; например, колонка «Тип • модель» — по типу */
  sortField?: string
}

/** props компонента ячейки */
export interface CellProps<Row, Value = unknown> {
  row: Row
  value: Value
  column: Column<Row>
}

/** props компонента заголовка */
export interface HeaderProps<Row> {
  column: Column<Row>
}

/** props компонента подвала */
export interface FooterProps<Row> {
  rows: Row[]
  column: Column<Row>
}

/**
 * Сортировка: поле для сервера (sortField колонки или field) и направление. Таблица сама не сортирует, а отдаёт
 * запрос наружу.
 */
export interface TableSort {
  field: string
  desc: boolean
}

/** Помощник для описания колонок: проверяет поля по типу строки. */
export function defineColumns<Row>(columns: Column<Row>[]): Column<Row>[] {
  return columns
}
