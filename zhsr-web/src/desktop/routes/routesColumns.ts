import type { Route } from '@/api/routes'
import { defineColumns } from '@/shared/components/table/types'
import CellRouteDuration from './cells/CellRouteDuration.vue'
import CellRouteKind from './cells/CellRouteKind.vue'
import CellRouteName from './cells/CellRouteName.vue'

/** Колонки таблицы «Маршруты». */
export const routesColumns = defineColumns<Route>([
  {
    field: 'name',
    title: 'Маршрут',
    width: 'minmax(14rem, 2fr)',
    cell: CellRouteName,
    sortable: true,
    sortField: 'train',
  },
  { field: 'kind', title: 'Вид', cell: CellRouteKind },
  { field: 'durationHours', title: 'Время в пути', cell: CellRouteDuration, sortable: true },
])
