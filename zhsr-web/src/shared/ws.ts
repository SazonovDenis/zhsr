import type { StompSubscription } from '@stomp/stompjs'
import { Client } from '@stomp/stompjs'

import { bus } from './bus'

/**
 * Постоянное соединение с сервером по WebSocket (протокол STOMP).
 *
 * Сервер шлёт сообщения в каналы: канал `<имя>` — это destination `/topic/<имя>` на сервере. Подписка переживает
 * разрывы: после переподключения клиент подписывается заново.
 *
 * После повторного подключения и при событии браузера online в шину уходит `connection-back`. Его слушает только
 * shared/user.ts: он сверяет локальную БД с сервером и отправляет `connection-restored`, по которому остальные обновляют
 * свои данные: за время разрыва могли пропасть сообщения сервера.
 *
 * Адрес — /api/ws: в dev-режиме он проксируется на сервер zhsr-app (quasar.config.ts > devServer.proxy).
 */

type Handler = (body: unknown) => void

const handlers = new Map<string, Set<Handler>>()
const subscriptions = new Map<string, StompSubscription>()

let wasConnected = false

const client = new Client({
  brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/api/ws`,
  reconnectDelay: 5000,
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
})

client.onConnect = () => {
  subscriptions.clear()
  for (const channel of handlers.keys()) subscribeChannel(channel)
  if (wasConnected) bus.emit('connection-back')
  wasConnected = true
}

window.addEventListener('online', () => bus.emit('connection-back'))

function subscribeChannel(channel: string) {
  const subscription = client.subscribe(`/topic/${channel}`, (message) => {
    let body: unknown
    try {
      body = JSON.parse(message.body)
    } catch {
      body = message.body
    }
    for (const handler of handlers.get(channel) ?? []) handler(body)
  })
  subscriptions.set(channel, subscription)
}

/** Подключиться к серверу. Вызывается один раз при старте приложения (src/boot/ws.ts). */
export function connectWs() {
  client.activate()
}

/** Подписаться на канал. Возвращает функцию отписки. */
export function subscribe(channel: string, handler: Handler): () => void {
  let set = handlers.get(channel)
  if (!set) {
    set = new Set()
    handlers.set(channel, set)
    if (client.connected) subscribeChannel(channel)
  }
  set.add(handler)

  return () => {
    set.delete(handler)
    if (set.size > 0) return
    handlers.delete(channel)
    subscriptions.get(channel)?.unsubscribe()
    subscriptions.delete(channel)
  }
}
