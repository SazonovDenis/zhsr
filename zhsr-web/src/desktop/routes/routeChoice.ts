import type { Route } from '@/api/routes'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import { dictManager } from '@/shared/dict/DictManager'

/**
 * Маршрут как значение CbList для строки RouteOption: текст — номер поезда и сообщение (по нему поиск и показ, пока
 * идёт ввод), поиск — ещё и по всем станциям маршрута, сам маршрут — в data.route.
 */
export function routeChoiceItem(route: Route): ChoiceItem {
  return {
    id: route.id,
    text: `№ ${route.train} • ${route.name}`,
    search: route.points.map((p) => dictManager.getValue('Station', p.station)).join(' '),
    data: { route },
  }
}

/** Маршрут из значения, собранного routeChoiceItem. */
export function routeOf(item: ChoiceItem): Route {
  return item.data!.route as Route
}
