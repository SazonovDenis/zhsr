import type { Route } from '@/api/routes'
import type { Trip } from '@/api/trips'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import { dictManager } from '@/shared/dict/DictManager'

/**
 * Рейс как значение CbList для строки TripOption: текст — номер поезда и маршрут, поиск — ещё и по станциям маршрута,
 * в data — сам рейс и его маршрут (route; null — маршрут не загружен).
 */
export function tripChoiceItem(trip: Trip, route: Route | null = null): ChoiceItem {
  return {
    id: trip.id,
    text: `№ ${trip.train} • ${trip.routeName}`,
    search: route?.points.map((p) => dictManager.getValue('Station', p.station)).join(' ') ?? '',
    data: { trip, route },
  }
}

/** Рейс из значения, собранного tripChoiceItem. */
export function tripOf(item: ChoiceItem): Trip {
  return item.data!.trip as Trip
}

/** Маршрут рейса из значения, собранного tripChoiceItem. */
export function tripRouteOf(item: ChoiceItem): Route | null {
  return (item.data!.route as Route | null) ?? null
}
