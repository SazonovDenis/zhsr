import { toRaw } from 'vue'

import { isNetworkError } from '@/api/http'
import { db } from '@/shared/db/db'

/**
 * Последние загруженные данные на телефоне (IndexedDB, таблица cache): без связи мобильная часть показывает их.
 *
 *   const { data, stale } = await loadCached(`crew:${id}`, () => fetchTripCrew(id))
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

/**
 * Все строки из сохранённых списков, ключи которых подходят под keys. Строка одна на несколько списков — берётся из
 * сохранённого последним.
 */
export async function cachedRows<T extends { id: number }>(keys: (key: string) => boolean): Promise<T[]> {
  const lists = await db.cache.filter((row) => keys(row.key)).toArray()
  lists.sort((a, b) => b.savedAt - a.savedAt)
  const rows = new Map<number, T>()
  for (const list of lists) {
    for (const row of list.data as T[]) {
      if (!rows.has(row.id)) rows.set(row.id, row)
    }
  }
  return [...rows.values()]
}

/** Строка с этим id из сохранённых списков; нет — null. */
export async function findCachedRow<T extends { id: number }>(
  keys: (key: string) => boolean,
  id: number,
): Promise<T | null> {
  return (await cachedRows<T>(keys)).find((x) => x.id === id) ?? null
}

/** Удалить копии, ключ которых начинается с prefix; пустой prefix — все (выход пользователя). */
export async function clearCached(prefix = ''): Promise<void> {
  await db.cache.filter((row) => row.key.startsWith(prefix)).delete()
}

/** Копия данных без реактивных обёрток Vue: IndexedDB сохраняет только обычные объекты. */
function plain<T>(data: T): T {
  return JSON.parse(JSON.stringify(toRaw(data))) as T
}
