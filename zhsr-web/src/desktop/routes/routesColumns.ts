import type { RouteRow } from '@/api/routes'
import { defineColumns } from '@/shared/components/table/types'
import CellRouteDuration from './cells/CellRouteDuration.vue'
import CellRouteKind from './cells/CellRouteKind.vue'
import CellRouteName from './cells/CellRouteName.vue'

/** Колонки таблицы «Маршруты». */
export const routesColumns = defineColumns<RouteRow>([
  {
    field: 'name',
    title: 'Маршрут',
    width: 'minmax(14rem, 2fr)',
    cell: CellRouteName,
    sortable: true,
    sortField: 'trainNumber',
  },
  // «Вид» — постоянной ширины, не тянется: вид маршрута и вид перевозки второй строкой
  { field: 'routeKind', title: 'Вид', width: '8rem', cell: CellRouteKind },
  // ширина постоянная, по самой длинной строке («23 ч 30 мин»), не тянется: по содержимому каждая строка сетки была бы
  // своей ширины
  { field: 'duration', title: 'В пути', width: '8.5rem', cell: CellRouteDuration, sortable: true },
])
