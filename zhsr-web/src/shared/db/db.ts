import type { EntityTable } from 'dexie'
import Dexie from 'dexie'

import type { TripRef } from '@/api/wagons'

/**
 * Локальная база приложения в IndexedDB (Dexie).
 *
 * Таблицы описываются в версиях схемы: чтобы добавить таблицу или индекс, допишите новую версию
 * `this.version(N + 1).stores({...})` — старые версии не меняются, Dexie переводит базу пользователя по цепочке.
 * В stores перечисляются только первичный ключ и индексы; остальные поля записи хранятся без объявления.
 *
 * В базе:
 * - справочники, нужные без сети (см. DictManager);
 * - cache — последние загруженные данные мобильной части: текущий пользователь, списки заявок, вагоны рейса
 *   (см. shared/offline/cache.ts);
 * - outbox — очередь отправки на сервер: заявки, исправления, примечания, фото (см. shared/offline/outbox.ts);
 * - meta — сведения о самой локальной БД: идентификатор серверной БД, от которой получены данные (см. serverDb.ts), и
 *   логин последнего вошедшего.
 */

/** Справочник: версия и время сохранения. Записи справочника — в таблице dictRecords. */
export interface DictMeta {
  name: string
  version: string
  savedAt: number
}

/** Запись справочника. */
export interface DictRecordRow {
  dict: string
  id: string | number
  rec: Record<string, unknown>
}

/** Сведение о локальной БД. */
export interface MetaRow {
  key: string
  value: string
}

/** Последние загруженные данные по ключу запроса. */
export interface CacheRow {
  key: string
  data: unknown
  savedAt: number
}

/** Ключ meta: логин последнего вошедшего — подставляется на странице входа. */
export const LAST_LOGIN = 'lastLogin'

/** Случайный guid записи: нужен только для уникальности, сервер принимает запись с одним guid один раз. */
export function randomGuid(): string {
  return 'xxxxxxxx-xxxx-4xxx-8xxx-xxxxxxxxxxxx'.replace(/x/g, () => Math.floor(Math.random() * 16).toString(16))
}

/** Что лежит в очереди: новая заявка, исправление заявки, примечание к заявке или одно фото к заявке. */
export type OutboxKind = 'request' | 'edit' | 'note' | 'photo' | 'action' | 'assign'

/** Сделанное без связи: уходит на сервер, когда связь появится. */
export interface OutboxRow {
  /** локальный id: «local-…» */
  id: string
  /** guid записи: защита от повторной отправки, с ним запись уходит на сервер */
  guid: string
  kind: OutboxKind
  /** заявка на сервере — id заявки (исправление, примечание, фото); null — id ещё нет */
  request: number | null
  /** заявка в очереди, к которой относится фото или примечание, — её guid; сервер её примет — проставится request */
  parent: string | null
  /** черновик заявки без фото (RequestDraft) — новая заявка и исправление */
  draft: Record<string, unknown>
  /** номер вагона — для показа, пока запись в очереди: в черновике вагон — id */
  wagonNumber: string
  /** тип вагона — id справочника WagonType, для показа */
  wagonType: number
  /** рейс — для показа: в черновике рейс — id */
  trip: TripRef | null
  /** текст примечания; у действия — комментарий; у назначения — имя исполнителя для показа */
  text: string
  /** действие над заявкой (RequestAction) — у записи action */
  action?: string
  /** исполнитель — id сотрудника, null — снять назначение; у записи assign */
  executor?: number | null
  /** фото: каждое фото — своя запись */
  photo: Blob | null
  /** когда пользователь это сделал, ISO */
  created: string
  /** автор — id сотрудника */
  author: number
  /** текст отказа сервера; пусто — отказа не было */
  error: string
  /** сервер отказал по существу: запись больше не отправляется, пока пользователь её не исправит или не отменит */
  refused: boolean
}

class ZhsrDb extends Dexie {
  dicts!: EntityTable<DictMeta, 'name'>
  dictRecords!: Dexie.Table<DictRecordRow, [string, string | number]>
  cache!: EntityTable<CacheRow, 'key'>
  outbox!: EntityTable<OutboxRow, 'id'>
  meta!: EntityTable<MetaRow, 'key'>

  constructor() {
    super('zhsr')
    this.version(1).stores({
      dicts: 'name',
      // ключ — справочник и id записи; индекс по справочнику — для выборки и удаления справочника целиком
      dictRecords: '[dict+id], dict',
    })
    this.version(2).stores({
      cache: 'key',
      outbox: 'id, created',
    })
    this.version(3).stores({
      meta: 'key',
    })
    // очередь — не только заявки: записи до этой версии — новые заявки
    this.version(4)
      .stores({})
      .upgrade((tx) =>
        tx
          .table('outbox')
          .toCollection()
          .modify((row: OutboxRow) => {
            row.kind = 'request'
            row.request = null
            row.wagonNumber ??= ''
            row.wagonType = 0
            row.trip = null
            row.text = ''
          }),
      )
    this.version(5)
      .stores({})
      .upgrade((tx) =>
        tx
          .table('outbox')
          .toCollection()
          .modify((row: OutboxRow) => {
            row.guid = randomGuid()
          }),
      )
    this.version(6)
      .stores({})
      .upgrade((tx) =>
        tx
          .table('outbox')
          .toCollection()
          .modify((row: OutboxRow) => {
            row.parent = null
          }),
      )
    // фото лежали внутри записи: теперь каждое фото — своя запись, заявку из очереди находят по её guid
    this.version(7)
      .stores({})
      .upgrade(async (tx) => {
        const table = tx.table('outbox')
        const rows = (await table.toArray()) as (OutboxRow & { photos?: Blob[] })[]
        for (const { photos = [], ...row } of rows) {
          await table.put({ ...row, photo: row.kind === 'photo' ? (photos[0] ?? null) : null })
          if (row.kind === 'photo') continue
          const link = row.kind === 'request' ? { parent: row.guid } : {}
          for (const photo of photos) {
            const id = `local-${randomGuid()}`
            const fields = { guid: randomGuid(), kind: 'photo', draft: {}, text: '', photo, error: '' }
            await table.add({ ...row, ...link, id, ...fields })
          }
        }
      })
    this.version(8)
      .stores({})
      .upgrade((tx) =>
        tx
          .table('outbox')
          .toCollection()
          .modify((row: OutboxRow) => {
            row.refused = false
          }),
      )
  }
}

export const db = new ZhsrDb()
