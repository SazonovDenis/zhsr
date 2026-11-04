import type { RepairRequest } from '@/api/requests'
import { defineColumns } from '@/shared/components/table/types'
import CellRequestExecutor from './cells/CellRequestExecutor.vue'
import CellRequestFault from './cells/CellRequestFault.vue'
import CellRequestNumber from './cells/CellRequestNumber.vue'
import CellRequestStatus from './cells/CellRequestStatus.vue'

/** Колонки таблицы «Заявки». */
export const requestsColumns = defineColumns<RepairRequest>([
  { field: 'number', title: '№', width: '9.5rem', cell: CellRequestNumber, sortable: true, sortField: 'createdDt' },
  { field: 'faultType', title: 'Неисправность', width: 'minmax(12rem, 1fr)', cell: CellRequestFault },
  { field: 'wagonNumber', title: 'Вагон', width: '6rem', cellClass: 'zs-mono', sortable: true },
  { field: 'executorUsr', title: 'Исполнитель', width: '11rem', cell: CellRequestExecutor },
  { field: 'requestStatusType', title: 'Статус', width: '9.5rem', cell: CellRequestStatus, sortable: true },
])
