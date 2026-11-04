import type { EntityTable } from 'dexie'
import Dexie from 'dexie'

/**
 * Локальная база приложения в IndexedDB (Dexie).
 *
 * Таблицы описываются в версиях схемы: чтобы добавить таблицу или индекс, допишите новую версию
 * `this.version(N + 1).stores({...})` — старые версии не меняются, Dexie переводит базу пользователя по цепочке.
 * В stores перечисляются только первичный ключ и индексы; остальные поля записи хранятся без объявления.
 *
 * В базе:
 * - справочники, нужные без сети (см. DictManager);
 * - cache — последние загруженные данные мобильной части: текущий пользователь, списки и карточки заявок, вагоны рейса
 *   (см. shared/offline/cache.ts);
 * - outbox — очередь заявок, поданных без связи (см. shared/offline/outbox.ts).
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

/** Последние загруженные данные по ключу запроса. */
export interface CacheRow {
  key: string
  data: unknown
  savedAt: number
}

/** Заявка, поданная без связи: черновик и фото; уходит на сервер, когда связь появится. */
export interface OutboxRow {
  /** локальный id: «local-…» */
  id: string
  /** черновик заявки без фото (RequestDraft) */
  draft: Record<string, unknown>
  /** номер вагона — для показа заявки в очереди: в черновике вагон — id; у записей до этого поля его нет */
  wagonNumber?: string
  photos: Blob[]
  /** когда подана, ISO */
  created: string
  /** автор — id сотрудника */
  author: number
  /** текст последней ошибки отправки, кроме ошибки связи; пусто — ошибок не было */
  error: string
}

class ZhsrDb extends Dexie {
  dicts!: EntityTable<DictMeta, 'name'>
  dictRecords!: Dexie.Table<DictRecordRow, [string, string | number]>
  cache!: EntityTable<CacheRow, 'key'>
  outbox!: EntityTable<OutboxRow, 'id'>

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
  }
}

export const db = new ZhsrDb()
