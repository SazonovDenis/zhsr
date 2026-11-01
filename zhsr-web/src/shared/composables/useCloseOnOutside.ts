import type { Ref } from 'vue'
import { onBeforeUnmount, onMounted } from 'vue'

/**
 * Свернуть раскрытый блок, как только он теряет фокус: клик мышью или переход фокуса (Tab) за его пределы.
 * Клик по месту, которое фокус не принимает, фокус не переносит, поэтому кроме перехода фокуса отслеживается и сам
 * клик.
 *
 *   useCloseOnOutside(groupEl, () => (open.value = false))
 */
export function useCloseOnOutside(el: Ref<HTMLElement | null>, close: () => void) {
  function onOutside(e: Event) {
    const target = e.target as Node | null
    if (el.value && target && !el.value.contains(target)) close()
  }

  onMounted(() => {
    document.addEventListener('pointerdown', onOutside, true)
    document.addEventListener('focusin', onOutside)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('pointerdown', onOutside, true)
    document.removeEventListener('focusin', onOutside)
  })
}
