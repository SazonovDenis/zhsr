/**
 * Вид маршрута: значения route.route_kind. Таблицы справочника нет: константы - как RouteKind.java на сервере.
 */
export const RouteKind = {
  /** Регулярный маршрут: поезд по расписанию */
  REGULAR: 1,
  /** Разовый маршрут: поезд без расписания */
  ONE_TIME: 2,
} as const

/** Записи справочника RouteKind для DictManager. */
export const ROUTE_KIND_RECORDS = [
  { id: RouteKind.REGULAR, text: 'Регулярный' },
  { id: RouteKind.ONE_TIME, text: 'Разовый' },
]
