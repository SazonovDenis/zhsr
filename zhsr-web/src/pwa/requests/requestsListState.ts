import { ref } from 'vue'

import type { RequestScope } from '@/api/requests'
import { onSessionReset } from '@/shared/sessionState'

/** Отбор списка заявок: открытые, закрытые или всё, что не отправлено. */
export type RequestsMode = 'open' | 'closed' | 'unsent'

/**
 * Отбор и прокрутка списка заявок мобильной части — в памяти приложения, вне страницы: страница уничтожается при
 * переходе в заявку, а возвращаться надо к тому же виду. В хранилище не пишется: обновление страницы всё сбрасывает.
 */
export const listScope = ref<RequestScope>('todo')
export const listMode = ref<RequestsMode>('open')

/** Прокрутка списка, пикселей. */
export const listState = { scrollTop: 0 }

// выход: отбор и прокрутка — не для следующего пользователя
onSessionReset(() => {
  listScope.value = 'todo'
  listMode.value = 'open'
  listState.scrollTop = 0
})
