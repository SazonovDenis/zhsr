import type { TableSort } from '@/shared/components/table/types'

/**
 * Сортировка фиктивных данных так, как её будет делать сервер. Нужна только заготовкам API, пока сервера нет.
 * Поле задаётся путём через точку: 'location.since'.
 */
export function fakeSort<T>(rows: T[], sort: TableSort | null): T[] {
  if (!sort) return rows
  const dir = sort.desc ? -1 : 1
  return [...rows].sort((a, b) => compare(valueAt(a, sort.field), valueAt(b, sort.field)) * dir)
}

function valueAt(row: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>((obj, key) => (obj as Record<string, unknown> | null)?.[key], row)
}

function compare(a: unknown, b: unknown): number {
  if (typeof a === 'number' && typeof b === 'number') return a - b
  return text(a).localeCompare(text(b), 'ru', { numeric: true })
}

function text(value: unknown): string {
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return value === null || value === undefined ? '' : JSON.stringify(value)
}
