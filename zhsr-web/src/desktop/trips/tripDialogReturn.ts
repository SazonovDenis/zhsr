import { shallowRef } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'

import type { TripDraft } from '@/api/trips'

/** Исправляемый рейс окна «Редактирование рейса»; null — «Создание рейса». */
export type TripDialogTrip = { id: number; route: number; plannedDt: string; train: number | null } | null

/** Окно рейса, которое надо открыть снова: что в нём было введено и на какой странице (pageKey). */
interface TripDialogReopen {
  trip: TripDialogTrip
  draft: TripDraft
  page: string
}

/** Страница без учёта параметров адреса (?…): имя и параметры пути — страница их может менять сама. */
export function pageKey(route: { name?: unknown; params: RouteLocationNormalizedLoaded['params'] }): string {
  return `${String(route.name)}:${JSON.stringify(route.params)}`
}

/** Окно рейса, которое надо открыть снова; null — не надо. Открывает окно useTripDialogReturn. */
export const tripDialogReopen = shallowRef<TripDialogReopen | null>(null)

/**
 * Снова открыть окно рейса на текущей странице с введённым draft: окно закрывалось на время создания нового маршрута
 * или поезда.
 */
export function reopenTripDialog(router: Router, trip: TripDialogTrip, draft: TripDraft): void {
  tripDialogReopen.value = { trip, draft: { ...draft }, page: pageKey(router.currentRoute.value) }
}
