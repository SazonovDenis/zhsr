import type { AdminUser } from '@/api/admin'
import { t } from '@/boot/i18n'
import { defineColumns } from '@/shared/components/table/types'
import { sortFields } from '../shared/adminFieldOrder'

/**
 * Колонки таблицы «Пользователи»; порядок — adminFieldOrder. names — тексты ролей, депо и категорий по id (варианты
 * /admin/options).
 */
export function usersColumns(names: { roles: Map<number, string>; depots: Map<number, string> }) {
  const columns = defineColumns<AdminUser>([
    { field: 'fullName', title: 'ФИО', width: 'minmax(14rem, 2fr)', sortable: true },
    { field: 'login', title: 'Логин', cellClass: 'zs-mono', sortable: true },
    {
      field: 'roles',
      title: 'Роли',
      width: 'minmax(12rem, 2fr)',
      format: (_, row) => row.roles.map((id) => t(names.roles.get(id) ?? String(id))).join(', '),
    },
    {
      field: 'depot',
      title: 'Депо',
      format: (_, row) => (row.depot === null ? '' : (names.depots.get(row.depot) ?? String(row.depot))),
    },
  ])
  return sortFields('usr', columns, (col) => col.field)
}
