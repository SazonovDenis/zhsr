import type { RouteOption } from '@/api/routes'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import { dictManager } from '@/shared/dict/DictManager'

/**
 * Маршрут как значение CbList для строки RouteOptionView: текст — номер поезда и сообщение (по нему поиск и показ, пока
 * идёт ввод), поиск — ещё и по остановкам маршрута, сам маршрут — в data.route.
 */
export function routeOptionItem(route: RouteOption): ChoiceItem {
  return {
    id: route.id,
    text: `№ ${route.trainNumber} • ${route.name}`,
    search: route.stops.map((station) => dictManager.getValue('Station', station)).join(' '),
    data: { route },
  }
}

/** Маршрут из значения, собранного routeOptionItem. */
export function routeOptionOf(item: ChoiceItem): RouteOption {
  return item.data!.route as RouteOption
}
