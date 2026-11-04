import type { TripRow } from '@/api/trips'
import { formatHm } from '@/desktop/routes/duration'
import { defineColumns } from '@/shared/components/table/types'
import CellTripDeparture from './cells/CellTripDeparture.vue'
import CellTripReadiness from './cells/CellTripReadiness.vue'
import CellTripRoute from './cells/CellTripRoute.vue'

/** Колонки таблицы «Рейсы». */
export const tripsColumns = defineColumns<TripRow>([
  {
    field: 'name',
    title: 'Маршрут',
    width: 'minmax(10rem, 1fr)',
    cell: CellTripRoute,
    sortable: true,
    sortField: 'trainNumber',
  },
  { field: 'duration', title: 'Время в пути', format: (minutes) => formatHm(minutes as number), sortable: true },
  { field: 'plannedDt', title: 'Отправление', cell: CellTripDeparture, sortable: true },
  { field: 'readiness', title: 'Готовность', width: '20rem', cell: CellTripReadiness },
])
