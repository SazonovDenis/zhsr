import { toRaw } from 'vue'

import { isNetworkError } from '@/api/http'
import { db } from '@/shared/db/db'

/**
 * Последние загруженные данные на телефоне (IndexedDB, таблица cache): без связи мобильная часть показывает их.
 *
 *   const { data, stale } = await loadCached(`request:${id}`, () => fetchRequest(id))
 *
 * Есть связь — данные загружаются с сервера и сохраняются под ключом. Нет связи (ошибка связи) — берётся сохранённая
 * копия, stale = true, savedAt — когда она сохранена. Копии нет — ошибка связи уходит вызывающему.
 */
export interface Cached<T> {
  data: T
  /** данные из копии на телефоне, а не с сервера */
  stale: boolean
  /** когда копия сохранена, мс */
  savedAt: number
}

export async function loadCached<T>(key: string, loader: () => Promise<T>): Promise<Cached<T>> {
  try {
    const data = await loader()
    const savedAt = Date.now()
    await db.cache.put({ key, data: plain(data), savedAt })
    return { data, stale: false, savedAt }
  } catch (e) {
    if (!isNetworkError(e)) throw e
    const row = await db.cache.get(key)
    if (!row) throw e
    return { data: row.data as T, stale: true, savedAt: row.savedAt }
  }
}

/** Сохранённая копия без загрузки; нет копии — null. */
export async function readCached<T>(key: string): Promise<T | null> {
  return ((await db.cache.get(key))?.data as T | undefined) ?? null
}

/** Удалить копии, ключ которых начинается с prefix; пустой prefix — все (выход пользователя). */
export async function clearCached(prefix = ''): Promise<void> {
  await db.cache.filter((row) => row.key.startsWith(prefix)).delete()
}

/** Копия данных без реактивных обёрток Vue: IndexedDB сохраняет только обычные объекты. */
function plain<T>(data: T): T {
  return JSON.parse(JSON.stringify(toRaw(data))) as T
}
