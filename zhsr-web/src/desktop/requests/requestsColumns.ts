import type { RepairRequest } from '@/api/requests'
import { defineColumns } from '@/shared/components/table/types'
import CellRequestExecutor from './cells/CellRequestExecutor.vue'
import CellRequestFault from './cells/CellRequestFault.vue'
import CellRequestNumber from './cells/CellRequestNumber.vue'
import CellRequestStatus from './cells/CellRequestStatus.vue'

/** Колонки таблицы «Заявки». */
export const requestsColumns = defineColumns<RepairRequest>([
  { field: 'number', title: '№', width: '9.5rem', cell: CellRequestNumber, sortable: true, sortField: 'created' },
  { field: 'fault', title: 'Неисправность', width: 'minmax(12rem, 1fr)', cell: CellRequestFault },
  { field: 'wagon', title: 'Вагон', width: '6rem', cellClass: 'zs-mono', sortable: true },
  { field: 'executor', title: 'Исполнитель', width: '11rem', cell: CellRequestExecutor },
  { field: 'status', title: 'Статус', width: '9.5rem', cell: CellRequestStatus, sortable: true },
])
