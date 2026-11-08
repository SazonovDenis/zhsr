import { computed, ref, shallowRef, watch } from 'vue'

import { isNetworkError } from '@/api/http'
import type { CurrentUser } from '@/api/session'
import type { RequestWagon } from '@/api/wagons'
import { fetchTripWagons, findWagons } from '@/api/wagons'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { loadCached, readCached } from '@/shared/offline/cache'
import { dictManager } from '@/shared/dict/DictManager'

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

/**
 * Выбор вагона в заявке (Ф-ЗАЯ-01) — общее для десктопной и мобильной частей.
 *
 * - По умолчанию — фильтр по текущему рейсу пользователя: вагоны состава рейса (fetchTripWagons). Подпись фильтра —
 *   номер поезда и маршрут рейса из данных пользователя.
 * - Пользователь снял фильтр или ввёл цифры номера — поиск по номеру среди всех вагонов (findWagons); фильтр по рейсу
 *   при поиске по номеру не действует.
 * - offline — мобильная часть: состав рейса берётся с телефона, если связи нет; поиск идёт по сохранённому составу.
 *   Заранее состав сохраняет saveTripWagons.
 */
export function useWagonPick(user: () => CurrentUser | null, offline = false) {
  /** фильтр по рейсу включён */
  const tripOn = ref(true)
  /** цифры номера вагона */
  const query = ref('')
  const wagons = shallowRef<RequestWagon[]>([])
  const loading = ref(false)
  /** вагоны состава рейса — для «Мой вагон» и поиска без связи */
  const tripWagons = shallowRef<RequestWagon[]>([])

  const trip = computed(() => user()?.trip ?? null)
  /** подпись фильтра: «Рейс 002 • Алматы — Астана» */
  const tripLabel = computed(() => (trip.value ? `${trip.value.trainNumber} • ${trip.value.name}` : ''))
  /** фильтр по рейсу есть и действует (поиск по номеру его отключает) */
  const tripActive = computed(() => !!trip.value && tripOn.value && !query.value)
  /** вагон проводника — первым в выборе */
  const myWagon = computed(() => {
    const number = trip.value?.wagons[0]
    return number ? (tripWagons.value.find((w) => w.number === number) ?? null) : null
  })

  async function loadTrip(): Promise<RequestWagon[]> {
    const id = trip.value?.id
    if (!id) return []
    if (!offline) return fetchTripWagons(id)
    return (await loadCached(tripWagonsKey(id), () => fetchTripWagons(id))).data
  }

  async function search(number: string): Promise<RequestWagon[]> {
    try {
      return await findWagons(number)
    } catch (e) {
      if (!offline || !isNetworkError(e)) throw e
      // без связи — поиск по сохранённому составу рейса
      const saved = (await readCached<RequestWagon[]>(tripWagonsKey(trip.value?.id ?? ''))) ?? []
      return saved.filter((w) => w.number.includes(number))
    }
  }

  /** ошибка загрузки списка вагонов; показывается на месте списка, клик — повтор (load) */
  const error = ref<ShownError | null>(null)

  // список вагонов — свой контрол: загрузка не входит в ожидание фрейма, спиннер и ошибка — на месте списка
  let seq = 0
  async function load() {
    const my = ++seq
    loading.value = true
    error.value = null
    try {
      // справочники строк списка (тип вагона, место) — параллельно с вагонами
      const [, list] = await Promise.all([
        dictManager.prepareDicts('WagonType', 'Station', 'Depot', 'Yard'),
        (async () => {
          if (trip.value && tripWagons.value.length === 0) tripWagons.value = await loadTrip()
          return tripActive.value ? tripWagons.value : search(query.value)
        })(),
      ])
      if (my === seq) wagons.value = list
    } catch (e) {
      if (my !== seq) return
      wagons.value = []
      error.value = shownError(e)
    } finally {
      if (my === seq) loading.value = false
    }
  }

  watch([tripOn, query], () => void load())

  return {
    tripOn,
    query,
    wagons,
    loading,
    error,
    tripLabel,
    tripActive,
    hasTrip: computed(() => !!trip.value),
    myWagon,
    load,
  }
}
