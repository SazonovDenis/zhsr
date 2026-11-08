import type { WagonJournalRow } from '@/api/wagons'
import { defineColumns } from '@/shared/components/table/types'
import { formatDateTime } from '@/shared/format'
import { userName } from '@/shared/user'
import CellJournalBasis from './cells/CellJournalBasis.vue'
import CellJournalLocation from './cells/CellJournalLocation.vue'
import CellJournalState from './cells/CellJournalState.vue'

/** Строка журнала с ключом для таблицы: у события нет своего id. */
export type JournalRow = WagonJournalRow & { key: number }

/** Колонки журнала вагона: дата, смена расположения, смена состояния, основание, кто. */
export const journalColumns = defineColumns<JournalRow>([
  { field: 'dbeg', title: 'Дата', width: '9rem', format: (v) => formatDateTime(v as string) },
  { field: 'location', title: 'Смена расположения', cell: CellJournalLocation },
  { field: 'wagonStatusType', title: 'Смена состояния', cell: CellJournalState },
  { field: 'basisType', title: 'Основание', cell: CellJournalBasis },
  { field: 'byUsr', title: 'Кто', format: (_, row) => (row.byUsr ? userName(row.byUsr) : '—') },
])
