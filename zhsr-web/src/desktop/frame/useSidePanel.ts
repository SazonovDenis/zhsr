import type { Ref } from 'vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import type { RowKey } from '@/shared/components/table/types'

/**
 * Правая панель фрейма со списком (DesktopFrame, слот right) — когда она показана.
 *
 * - Места хватает (панель стоит рядом со списком): панель видна всегда; пока ничего не выбрано, в ней подсказка
 *   «Выберите … в списке». Закрыли панель (крестик, Escape) — она скрыта до следующего выбора.
 * - Места не хватает (панель плавает поверх списка): панель видна, только пока что-то выбрано.
 * - Хватает ли места, решает DesktopFrame и сообщает через v-model:floating в panel.floating.
 * - Escape закрывает панель: снимает выбор.
 *
 *   const panel = useSidePanel(selectedId)
 *   <DesktopFrame v-model:floating="panel.floating.value" :right-open="panel.open.value">
 *     … <WagonPanel @close="panel.close" /> …
 */
export function useSidePanel(selectedId: Ref<RowKey | null>) {
  // панель закрыли сами: на широком окне она не показывается пустой до следующего выбора
  const closed = ref(false)
  // панель плавает поверх списка: места рядом с ним не хватает (решает DesktopFrame)
  const floating = ref(false)

  watch(selectedId, (id) => {
    if (id !== null) closed.value = false
  })

  const open = computed(() => selectedId.value !== null || (!floating.value && !closed.value))

  function close() {
    selectedId.value = null
    closed.value = true
  }

  function onKeydown(e: KeyboardEvent) {
    // Escape внутри диалога до сюда не доходит: диалог его останавливает (DialogFrame)
    if (e.key === 'Escape' && open.value) close()
  }

  window.addEventListener('keydown', onKeydown)
  onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

  return { open, close, floating }
}
