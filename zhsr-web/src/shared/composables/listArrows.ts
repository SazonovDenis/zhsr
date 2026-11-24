/**
 * Стрелки вверх и вниз в списке из элементов DOM (не DataTable): элемент, на который переходит стрелка. Обработчик
 * keydown — на контейнере списка. Текущего нет — первый (вниз) или последний (вверх); клавиша не стрелка или строка
 * крайняя — null.
 *
 * @param items   селектор строк списка
 * @param current селектор текущей строки
 */
export function arrowTarget(e: KeyboardEvent, items: string, current: string): HTMLElement | null {
  if (e.ctrlKey || e.altKey || e.metaKey || e.shiftKey) return null
  if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return null
  e.preventDefault()
  const down = e.key === 'ArrowDown'
  const list = Array.from((e.currentTarget as HTMLElement).querySelectorAll<HTMLElement>(items))
  const at = list.findIndex((el) => el.matches(current))
  const next = at < 0 ? (down ? 0 : list.length - 1) : at + (down ? 1 : -1)
  return list[next] ?? null
}
