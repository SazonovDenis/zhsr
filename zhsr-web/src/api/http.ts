import axios from 'axios'

// HTTP-клиент сервера zhsr-app; в dev-режиме /api проксируется (quasar.config.ts > devServer.proxy)
export const http = axios.create({ baseURL: '/api' })

/** Запрос не дошёл до сервера: нет связи. */
export class NetworkError extends Error {
  constructor() {
    super('Нет связи')
  }
}

/** Ошибка связи, а не ответ сервера: мобильная часть по ней берёт данные из копии на телефоне или ставит ввод в очередь. */
export function isNetworkError(e: unknown): boolean {
  return e instanceof NetworkError || (axios.isAxiosError(e) && !e.response)
}

/**
 * Проверка связи для фиктивных данных: без сети фиктивный вызов падает так же, как упал бы настоящий запрос. Так
 * работу без связи можно проверить, выключив сеть в DevTools. Нужна только заготовкам API, пока сервера нет.
 */
export function fakeNetwork(): void {
  if (!navigator.onLine) throw new NetworkError()
}
