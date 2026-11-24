import type { Ref } from 'vue'
import { onBeforeUnmount } from 'vue'

/**
 * Escape в поле поиска или в списке диалога: заполненный поиск очищается, дальше (панель, диалог, «назад») Escape не
 * идёт; пустой поиск Escape не трогает.
 */
export function searchEsc(e: KeyboardEvent, search: string, clear: () => void) {
  if (!search || e.defaultPrevented) return
  e.preventDefault()
  e.stopPropagation()
  clear()
}

/**
 * Escape на странице со списком: заполненный поиск очищается, где бы ни был фокус. Строка выбрана (selected не null) —
 * Escape сначала снимает выбор (это делает панель или таблица), следующий очищает поиск. selected не передан — поиск
 * очищается сразу. На document — раньше обработчиков панели и «назад» (window).
 */
export function usePageSearchEsc(search: () => string, clear: () => void, selected?: Ref<unknown>) {
  function onEscape(e: KeyboardEvent) {
    if (e.key !== 'Escape' || (selected && selected.value !== null)) return
    // открыто меню или диалог — Escape их
    if (document.querySelector('.q-dialog, .q-menu, .row-menu')) return
    searchEsc(e, search(), clear)
  }

  document.addEventListener('keydown', onEscape)
  onBeforeUnmount(() => document.removeEventListener('keydown', onEscape))
}
