import type { Texts } from '@/api/checks'
import type { Column } from '@/shared/components/table/types'
import { sortOrderColumn } from '../shared/sortOrder'

/**
 * Колонки названия на трёх языках и порядка показа (с кнопками перестановки) — общие для таблиц администрирования
 * чек-листов и описей.
 */
export function textColumns<Row extends Texts & { sortOrder: number }>(): Column<Row>[] {
  return [
    sortOrderColumn<Row>('sortOrder'),
    { field: 'textRu', title: 'Название', width: 'minmax(14rem, 2fr)' },
    { field: 'textKk', title: 'На казахском', width: 'minmax(10rem, 1fr)', format: (v) => (v as string | null) ?? '' },
    { field: 'textEn', title: 'На английском', width: 'minmax(10rem, 1fr)', format: (v) => (v as string | null) ?? '' },
  ] as Column<Row>[]
}
