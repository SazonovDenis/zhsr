import type { RowMenuItem } from './types'

/** Действие со строкой, уже вычисленное для неё: текст, почему недоступно (пусто — доступно) и запуск. */
export interface RowAction {
  /** текст на русском; переводится при показе */
  label: string
  /** почему недоступно — переведённый текст; пусто — доступно */
  reason: string
  run: () => void
}

/**
 * Меню строки DataTable из функции, которая считает действия строки — те же, что у кнопок панели: пункт виден, если
 * функция вернула действие с таким текстом, и неактивен с его причиной.
 *
 * @param labels    все пункты по порядку — тексты действий на русском
 * @param actionsOf действия строки
 * @param separated пункты, над которыми черта
 */
export function actionsMenu<Row>(
  labels: string[],
  actionsOf: (row: Row) => RowAction[],
  separated: string[] = [],
): RowMenuItem<Row>[] {
  const find = (row: Row, label: string) => actionsOf(row).find((a) => a.label === label)
  return labels.map((label) => ({
    label,
    separator: separated.includes(label),
    visible: (row: Row) => !!find(row, label),
    disabled: (row: Row) => find(row, label)?.reason ?? '',
    run: (row: Row) => find(row, label)?.run(),
  }))
}
