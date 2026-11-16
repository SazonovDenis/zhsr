import type { InjectionKey } from 'vue'
import { inject, provide } from 'vue'

import type { Column } from '@/shared/components/table/types'
import CellSortOrder from './CellSortOrder.vue'

/**
 * Перестановка строк кнопками «вверх» и «вниз» в колонке «Порядок показа» (сервер: SortOrderService). Страница отдаёт
 * ячейкам, что можно и как переставлять (provideSortOrder); ячейка показывает число и кнопки (CellSortOrder).
 */
export interface SortOrderContext<Row> {
  /** кнопки доступны: таблица отсортирована по порядку показа; иначе — почему нет (переведённый текст) */
  reason: () => string
  /** можно ли переставить строку: вверх — не первая в своей группе, вниз — не последняя */
  canMove: (row: Row, up: boolean) => boolean
  /** переставить строку и обновить список */
  move: (row: Row, up: boolean) => Promise<void>
}

const KEY: InjectionKey<SortOrderContext<unknown>> = Symbol('sortOrder')

export function provideSortOrder<Row>(context: SortOrderContext<Row>): void {
  provide(KEY, context as SortOrderContext<unknown>)
}

export function useSortOrder(): SortOrderContext<unknown> | null {
  return inject(KEY, null)
}

/**
 * Колонка «Порядок»: первая в таблице, узкая, только кнопки перестановки — без числа. field — поле строки с порядком.
 */
export function sortOrderColumn<Row>(field: keyof Row & string): Column<Row> {
  return { field, title: 'Порядок', width: '6rem', align: 'center', cell: CellSortOrder, sortable: true }
}

/**
 * Сортировка таблицы, строки которой всегда в порядке показа: значок сортировки на колонке «Порядок». field — поле
 * строки с порядком.
 */
export function orderSort(field: string) {
  return { field, desc: false }
}

/**
 * Можно ли переставить строку в списке своей группы: group — строки группы в порядке показа.
 */
export function canMoveIn<T>(group: T[], row: T, up: boolean): boolean {
  const i = group.indexOf(row)
  return i >= 0 && (up ? i > 0 : i < group.length - 1)
}
