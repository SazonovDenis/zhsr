import type { Wagon } from '@/api/wagons'
import { defineColumns } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'
import CellWagonLocation from './cells/CellWagonLocation.vue'
import CellWagonState from './cells/CellWagonState.vue'
import CellWagonType from './cells/CellWagonType.vue'

/** Колонки таблицы «Вагоны». */
export const wagonsColumns = defineColumns<Wagon>([
  { field: 'number', title: 'Номер', width: '8ch', cellClass: 'zs-mono', sortable: true },
  { field: 'type', title: 'Тип • модель', cell: CellWagonType },
  { field: 'depot', title: 'Депо приписки', format: (depot) => dictManager.getValue('Depot', depot as string) },
  { field: 'location', title: 'Расположение', cell: CellWagonLocation, sortable: true, sortField: 'location.since' },
  { field: 'state', title: 'Состояние', cell: CellWagonState, sortable: true, sortField: 'stateSince' },
])
