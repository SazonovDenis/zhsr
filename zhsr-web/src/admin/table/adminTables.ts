import type { AdminColumn, AdminOption, AdminRow, AdminTable } from '@/api/admin'
import { fetchAdminOptions, fetchAdminTables } from '@/api/admin'
import { t } from '@/boot/i18n'
import type { Column } from '@/shared/components/table/types'
import { defineColumns } from '@/shared/components/table/types'
import { sortFields } from '../shared/adminFieldOrder'

/**
 * Описания таблиц общего редактора и варианты полей-ссылок: загружаются с сервера один раз за сеанс страницы.
 */

let tables: Promise<AdminTable[]> | null = null
const options = new Map<string, Promise<AdminOption[]>>()

/** Описание таблицы; null — таблица не редактируется в администрировании. */
export async function adminTable(name: string): Promise<AdminTable | null> {
  tables ??= fetchAdminTables().catch((e: unknown) => {
    tables = null
    throw e
  })
  return (await tables).find((table) => table.name === name) ?? null
}

/** Варианты для поля-ссылки на таблицу ref. */
export function adminOptions(ref: string): Promise<AdminOption[]> {
  let list = options.get(ref)
  if (!list) {
    list = fetchAdminOptions(ref).catch((e: unknown) => {
      options.delete(ref)
      throw e
    })
    options.set(ref, list)
  }
  return list
}

/** Забыть загруженные варианты таблицы: она изменилась, при следующем показе они загрузятся заново. */
export function forgetAdminOptions(ref: string): void {
  options.delete(ref)
}

/** Колонки DataTable по полям таблицы: ссылка показывается текстом записи, «да/нет» — словом. */
export function adminColumns(table: AdminTable): Column<AdminRow>[] {
  return defineColumns<AdminRow>(
    sortFields(table.name, table.columns, (col) => col.name)
      .filter((col) => col.listed)
      .map((col) => ({
        field: col.type === 'REF' ? `${col.name}_text` : col.name,
        title: col.title,
        sortable: true,
        sortField: col.name,
        align: col.type === 'INT' || col.type === 'DECIMAL' ? 'right' : 'left',
        format: (value: unknown) => formatValue(col, value),
      })),
  )
}

function formatValue(col: AdminColumn, value: unknown): string {
  if (value === null || value === undefined) return ''
  if (col.type === 'BOOL') return value === true ? t('Да') : ''
  return typeof value === 'string' ? value : JSON.stringify(value)
}
