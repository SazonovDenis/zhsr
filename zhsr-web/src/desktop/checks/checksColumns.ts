import type { CheckListRow } from '@/api/checks'
import { textOf } from '@/shared/checks/texts'
import { defineColumns } from '@/shared/components/table/types'
import { formatDateTime } from '@/shared/format'
import { tripRefText } from '@/shared/trips/tripRefText'
import { userName } from '@/shared/user'

/** Объект проверки строкой: вагон, сотрудник или рейс. */
export function checkSubjectText(row: CheckListRow): string {
  if (row.wagon) return row.wagon.number
  if (row.checkedUsr) return userName(row.checkedUsr)
  return row.trip ? tripRefText(row.trip) : ''
}

/** Колонки списка проверок (Ф-ЧЕК-04). */
export const checksColumns = defineColumns<CheckListRow>([
  { field: 'createdDt', title: 'Начата', width: '9rem', format: (v) => formatDateTime(v as string) },
  {
    field: 'checkListTemplate',
    title: 'Чек-лист',
    width: 'minmax(14rem, 2fr)',
    format: (_, row) => textOf(row.checkListTemplate),
  },
  { field: 'wagon', title: 'Объект проверки', width: 'minmax(10rem, 1fr)', format: (_, row) => checkSubjectText(row) },
  {
    field: 'createdByUsr',
    title: 'Проверяющий',
    width: 'minmax(10rem, 1fr)',
    format: (_, row) => userName(row.createdByUsr),
  },
  { field: 'violationCount', title: 'Нарушений', width: '8rem', align: 'right' },
  { field: 'requestCount', title: 'Заявок', width: '7rem', align: 'right' },
])
