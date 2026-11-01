import type { Trip } from '@/api/trips'
import { t } from '@/boot/i18n'
import { defineColumns } from '@/shared/components/table/types'
import CellTripDeparture from './cells/CellTripDeparture.vue'
import CellTripReadiness from './cells/CellTripReadiness.vue'
import CellTripRoute from './cells/CellTripRoute.vue'

/** Колонки таблицы «Рейсы». */
export const tripsColumns = defineColumns<Trip>([
  {
    field: 'routeName',
    title: 'Маршрут',
    width: 'minmax(10rem, 1fr)',
    cell: CellTripRoute,
    sortable: true,
    sortField: 'train',
  },
  { field: 'durationHours', title: 'Время в пути', format: (hours) => t('{n} часов', hours as number), sortable: true },
  { field: 'planned', title: 'Отправление', cell: CellTripDeparture, sortable: true },
  { field: 'readiness', title: 'Готовность', width: '20rem', cell: CellTripReadiness },
])
