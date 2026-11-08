import type { WagonRow } from '@/api/wagons'
import { defineColumns } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'
import CellWagonLocation from './cells/CellWagonLocation.vue'
import CellWagonState from './cells/CellWagonState.vue'
import CellWagonType from './cells/CellWagonType.vue'

/** Колонки таблицы «Вагоны». */
export const wagonsColumns = defineColumns<WagonRow>([
  { field: 'number', title: 'Номер', width: '8ch', cellClass: 'zs-mono', sortable: true },
  { field: 'wagonType', title: 'Тип', width: 'minmax(min-content, 0.67fr)', cell: CellWagonType },
  {
    field: 'depot',
    title: 'Депо приписки',
    width: 'minmax(min-content, 0.8fr)',
    format: (depot) => dictManager.getValue('Depot', depot as number | null),
  },
  { field: 'locationType', title: 'Расположение', cell: CellWagonLocation, sortable: true, sortField: 'dbegLocation' },
  { field: 'wagonStatusType', title: 'Состояние', cell: CellWagonState, sortable: true, sortField: 'dbegStatus' },
])
