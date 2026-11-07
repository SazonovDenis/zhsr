import { ref } from 'vue'

import { onSessionReset } from '@/shared/sessionState'

/**
 * Какой рейс показан на вкладке «Рейс»: текущий или соседний (предыдущий или следующий). В памяти приложения, вне
 * страницы: выбор переживает переход в другие вкладки; выход и обновление страницы его сбрасывают.
 */
export const showOtherTrip = ref(false)

onSessionReset(() => {
  showOtherTrip.value = false
})
