import type { CheckListRow } from '@/api/checks'
import { t } from '@/boot/i18n'
import { textOf } from '@/shared/checks/texts'
import { defineColumns } from '@/shared/components/table/types'
import { formatDateTime } from '@/shared/format'
import { tripRefText } from '@/shared/trips/tripRefText'
import { userName } from '@/shared/user'

/** Объект проверки строкой: «вагон 49385», сотрудник или рейс. */
export function checkSubjectText(row: CheckListRow): string {
  if (row.wagon) return t('вагон {n}', { n: row.wagon.number })
  if (row.checkedUsr) return userName(row.checkedUsr)
  return row.trip ? tripRefText(row.trip) : ''
}

/** Колонки списка проверок (Ф-ЧЕК-04). */
export const checksColumns = defineColumns<CheckListRow>([
  {
    field: 'createdDt',
    title: 'Начата',
    // по ширине даты «17.11 17:32» (цифры по 1ch, точка, двоеточие и пробел уже) и отступов слева 1rem и справа (dense);
    // дата не переносится
    width: 'calc(9.5ch + 1.3rem)',
    dense: true,
    cellClass: 'text-no-wrap',
    sortable: true,
    format: (v) => formatDateTime(v as string),
  },
  // вид проверки — без сортировки
  {
    field: 'checkListTemplate',
    title: 'Чек-лист',
    width: 'minmax(14rem, 2fr)',
    format: (_, row) => textOf(row.checkListTemplate),
  },
  {
    field: 'wagon',
    title: 'Объект проверки',
    width: 'minmax(10rem, 1fr)',
    sortable: true,
    format: (_, row) => checkSubjectText(row),
  },
  {
    field: 'createdByUsr',
    title: 'Проверяющий',
    width: 'minmax(10rem, 1fr)',
    sortable: true,
    format: (_, row) => userName(row.createdByUsr),
  },
  // числа — по ширине заголовка; ширина постоянная: у каждой строки таблицы своя сетка, ширина по содержимому
  // разъехалась бы по строкам
  // нарушений нет — «нет»; заявок нет — пусто
  {
    field: 'violationCount',
    title: 'Нарушений',
    width: '6.5rem',
    align: 'right',
    format: (_, row) => (row.violationCount ? String(row.violationCount) : t('нет')),
  },
  {
    field: 'requestCount',
    title: 'Заявок',
    width: '5rem',
    align: 'right',
    format: (_, row) => (row.requestCount ? String(row.requestCount) : ''),
  },
])
