import { isNetworkError } from '@/api/http'
import type { RequestWagon } from '@/api/wagons'
import { fetchTripWagons } from '@/api/wagons'
import { loadCached } from '@/shared/offline/cache'

/** Ключ сохранённого на телефоне состава рейса. */
const tripWagonsKey = (tripId: number | string) => `trip-wagons:${tripId}`

/**
 * Сохранить состав рейса на телефоне заранее: без связи он нужен для выбора вагона в заявке. Нет связи — остаётся
 * прежняя копия, это не ошибка: сохранение идёт само, его никто не ждёт.
 */
export async function saveTripWagons(tripId: number): Promise<void> {
  try {
    await loadCached(tripWagonsKey(tripId), () => fetchTripWagons(tripId))
  } catch (e) {
    if (!isNetworkError(e)) throw e
  }
}

/** Состав рейса; offline — без связи берётся сохранённый на телефоне (saveTripWagons). */
export async function loadTripWagons(tripId: number, offline: boolean): Promise<RequestWagon[]> {
  if (!offline) return fetchTripWagons(tripId)
  return (await loadCached(tripWagonsKey(tripId), () => fetchTripWagons(tripId))).data
}
