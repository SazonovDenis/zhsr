import { Notify } from 'quasar'
import type { Router } from 'vue-router'

import { fetchTripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import { notifyError } from '@/shared/errors'

/**
 * Открыть рейс на экране поезда (Ф-ПОЕ-02, Ф-РЕЙ-02): состав поезда на отправление рейса, журнал — записи рейса.
 * train — поезд рейса, если известен; иначе он загружается с рейсом. У рейса нет поезда — сообщение, перехода нет;
 * ошибка загрузки — сообщением.
 */
export async function openTripTrain(router: Router, trip: number, train?: number | null): Promise<void> {
  let id = train ?? null
  if (train === undefined) {
    try {
      id = (await fetchTripCrew(trip)).trip.train?.id ?? null
    } catch (e) {
      notifyError(e)
      return
    }
  }
  if (id === null) {
    Notify.create({ message: t('У рейса не назначен поезд'), type: 'warning', position: 'bottom' })
    return
  }
  await router.push({ name: 'train-form', params: { id }, query: { trip: String(trip) } })
}
