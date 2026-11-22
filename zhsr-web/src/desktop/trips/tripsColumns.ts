import type { TripRow } from '@/api/trips'
import { defineColumns } from '@/shared/components/table/types'
import CellTripDeparture from './cells/CellTripDeparture.vue'
import CellTripPath from './cells/CellTripPath.vue'
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
  // ширина постоянная, по самой длинной строке («23 ч 30 мин»), не тянется: по содержимому каждая строка сетки была бы
  // своей ширины
  { field: 'duration', title: 'В пути', width: '8.5rem', cell: CellTripPath, sortable: true },
  // ширина постоянная, не тянется: состояние, время отправления и опоздание помещаются, длинное «в пути» — переносом
  { field: 'plannedDt', title: 'Отправление', width: '14rem', cell: CellTripDeparture, sortable: true },
  // тянутся только «Маршрут» и «Готовность»: остальные колонки постоянной ширины
  { field: 'readiness', title: 'Готовность', width: 'minmax(16rem, 1fr)', cell: CellTripReadiness },
])
