import type { EntityTable } from 'dexie'
import Dexie from 'dexie'

/**
 * Локальная база приложения в IndexedDB (Dexie).
 *
 * Таблицы описываются в версиях схемы: чтобы добавить таблицу или индекс, допишите новую версию
 * `this.version(N + 1).stores({...})` — старые версии не меняются, Dexie переводит базу пользователя по цепочке.
 * В stores перечисляются только первичный ключ и индексы; остальные поля записи хранятся без объявления.
 *
 * Сейчас в базе справочники, нужные без сети (см. DictManager). Очередь неотправленного ввода пользователя будет
 * отдельной таблицей в следующей версии схемы.
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

class ZhsrDb extends Dexie {
  dicts!: EntityTable<DictMeta, 'name'>
  dictRecords!: Dexie.Table<DictRecordRow, [string, string | number]>

  constructor() {
    super('zhsr')
    this.version(1).stores({
      dicts: 'name',
      // ключ — справочник и id записи; индекс по справочнику — для выборки и удаления справочника целиком
      dictRecords: '[dict+id], dict',
    })
  }
}

export const db = new ZhsrDb()
