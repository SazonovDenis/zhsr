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
  { field: 'routeKind', title: 'Вид', cell: CellRouteKind },
  { field: 'duration', title: 'Время в пути', cell: CellRouteDuration, sortable: true },
])
