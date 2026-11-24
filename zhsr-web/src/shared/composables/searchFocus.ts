import type { Ref } from 'vue'
import { onBeforeUnmount } from 'vue'

/**
 * Ctrl+F (в любой раскладке) — фокус в поле поиска страницы или диалога, текст выделяется; поиск браузера не открывается.
 * Открыт диалог — работает только поле верхнего диалога. Захват на window: диалог останавливает клавиши у себя.
 */
export function useSearchFocus(field: Readonly<Ref<{ $el: unknown } | null>>) {
  function onKeydown(e: KeyboardEvent) {
    if (e.code !== 'KeyF' || !(e.ctrlKey || e.metaKey) || e.altKey || e.shiftKey) return
    const root = field.value?.$el as HTMLElement | undefined
    const dialogs = document.querySelectorAll('.q-dialog')
    const top = dialogs[dialogs.length - 1] ?? document
    const input = root?.querySelector('input')
    if (!input || !top.contains(input)) return
    e.preventDefault()
    input.focus()
    input.select()
  }

  window.addEventListener('keydown', onKeydown, true)
  onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown, true))
}
