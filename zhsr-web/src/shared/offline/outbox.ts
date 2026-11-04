import { ref } from 'vue'

import { isNetworkError } from '@/api/http'
import type { RepairRequest, RequestDraft } from '@/api/requests'
import { createRequest } from '@/api/requests'
import { bus } from '@/shared/bus'
import { RequestStatus } from '@/shared/constants/systemDicts'
import type { OutboxRow } from '@/shared/db/db'
import { db } from '@/shared/db/db'
import { isoDateTime } from '@/shared/format'
import { isOnline } from '@/shared/network'
import { faultDefaults } from '@/shared/requests/faults'

/**
 * Очередь заявок, поданных без связи (IndexedDB, таблица outbox). Заявка ждёт на телефоне вместе с фото и уходит на
 * сервер сама, когда связь появится (событие браузера online, восстановление соединения), или по кнопке «Отправить».
 * Номер заявке присваивает сервер при отправке.
 *
 * После отправки в шину уходят requests-refresh и state-refresh: списки и счётчики загружаются заново.
 */

/** Заявка в очереди. */
export interface OutboxRequest {
  id: string
  draft: Omit<RequestDraft, 'photos'>
  /** номер вагона — для показа заявки в очереди */
  wagonNumber: string
  photos: Blob[]
  /** время подачи, ISO с часовым поясом */
  created: string
  /** автор — id сотрудника */
  author: number
  /** ошибка отправки, кроме ошибки связи: сервер не принял заявку */
  error: string
}

/** Заявки в очереди, от новых к старым; реактивно — для шапки и списков мобильной части. */
export const outbox = ref<OutboxRequest[]>([])

let started = false
let flushing: Promise<number> | null = null

/** Прочитать очередь и подписаться на появление связи. Вызывается мобильной частью при старте. */
export async function startOutbox(): Promise<void> {
  if (started) return
  started = true
  bus.on('connection-restored', () => void flushOutbox())
  await reload()
  if (isOnline.value) void flushOutbox()
}

/** Поставить заявку в очередь. wagonNumber — номер вагона черновика: для показа, пока заявка в очереди. */
export async function queueRequest(draft: RequestDraft, author: number, wagonNumber: string): Promise<OutboxRequest> {
  const { photos, ...rest } = draft
  const row: OutboxRow = {
    id: `local-${Date.now()}`,
    draft: { ...rest },
    wagonNumber,
    photos: [...photos],
    created: isoDateTime(new Date()),
    author,
    error: '',
  }
  await db.outbox.put(row)
  await reload()
  return toItem(row)
}

/** Заявка из очереди по локальному id; нет — null (уже отправлена). */
export function outboxItem(id: string): OutboxRequest | null {
  return outbox.value.find((x) => x.id === id) ?? null
}

/**
 * Отправить очередь по порядку подачи. Возвращает, сколько заявок ушло. Пропала связь — отправка останавливается до
 * следующей попытки; сервер не принял заявку — она остаётся в очереди с текстом ошибки.
 */
export function flushOutbox(): Promise<number> {
  flushing ??= send().finally(() => (flushing = null))
  return flushing
}

async function send(): Promise<number> {
  let sent = 0
  const rows = await db.outbox.orderBy('created').toArray()
  for (const row of rows) {
    try {
      await createRequest({ ...(row.draft as Omit<RequestDraft, 'photos'>), photos: row.photos })
      await db.outbox.delete(row.id)
      sent++
    } catch (e) {
      if (isNetworkError(e)) break
      await db.outbox.update(row.id, { error: e instanceof Error ? e.message : String(e) })
    }
  }
  await reload()
  if (sent > 0) {
    bus.emit('requests-refresh')
    bus.emit('state-refresh')
  }
  return sent
}

async function reload() {
  const rows = await db.outbox.orderBy('created').reverse().toArray()
  outbox.value = rows.map(toItem)
}

function toItem(row: OutboxRow): OutboxRequest {
  return { ...row, draft: row.draft as Omit<RequestDraft, 'photos'>, wagonNumber: row.wagonNumber ?? '' }
}

/** Заявка из очереди в виде строки списка: id — локальный, «local-…». */
export type QueuedRequest = Omit<RepairRequest, 'id'> & { id: string }

/** Заявка из очереди в виде строки списка: номера ещё нет, место вагона и тип — из выбора при подаче. */
export function outboxAsRequest(item: OutboxRequest, authorName: string, wagonType = 0): QueuedRequest {
  return {
    id: item.id,
    number: '',
    wagon: item.draft.wagon,
    wagonNumber: item.wagonNumber,
    wagonType,
    locationType: null,
    place: null,
    wagonTrip: null,
    wagonTripName: null,
    wagonStation: null,
    trip: null,
    service: faultDefaults(item.draft.faultType).service,
    faultType: item.draft.faultType,
    severityType: item.draft.severityType,
    createdByUsr: { id: item.author, name: authorName },
    createdDt: item.created,
    description: item.draft.description,
    requestStatusType: RequestStatus.NEW,
    executorUsr: null,
    tor: false,
    forwarded: false,
    photoCount: item.photos.length,
  }
}
