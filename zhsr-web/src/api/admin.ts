import type { TableSort } from '@/shared/components/table/types'
import { http } from './http'

/**
 * API администрирования: общий редактор таблиц (справочники, которые ведёт администратор) и редактор сотрудников.
 * Доступно только роли «Администратор компании».
 */

/** Вид значения поля. */
export type AdminColumnType = 'TEXT' | 'INT' | 'DECIMAL' | 'BOOL' | 'DATE' | 'REF'

/** Поле таблицы: из него строятся колонка таблицы и поле формы. */
export interface AdminColumn {
  /** имя колонки в БД; оно же поле строки */
  name: string
  /** заголовок на русском; переводится при показе */
  title: string
  type: AdminColumnType
  required: boolean
  /** наибольшая длина строки; null — не ограничена */
  maxLength: number | null
  /** таблица, на которую ссылается поле REF: варианты — fetchAdminOptions(ref), текст в строке — поле `<name>_text` */
  ref: string | null
  /** показывается колонкой в таблице */
  listed: boolean
}

export interface AdminTable {
  /** имя таблицы в БД */
  name: string
  /** заголовок на русском */
  title: string
  columns: AdminColumn[]
}

/** Строка таблицы: id, поля и `<поле>_text` — текст ссылки. */
export type AdminRow = { id: number } & Record<string, unknown>

/** Страница строк: строк по поиску всего и строки страницы. */
export interface AdminPage<T> {
  total: number
  rows: T[]
}

/** Запрос страницы: поиск, номер страницы с 0, размер страницы и сортировка. */
export interface AdminQuery {
  search: string
  page: number
  size: number
  sort: TableSort | null
}

/** Вариант для поля-ссылки. */
export interface AdminOption {
  id: number
  text: string
}

function params(query: AdminQuery) {
  return { search: query.search, page: query.page, size: query.size, sort: query.sort?.field, desc: query.sort?.desc }
}

/** Таблицы общего редактора с полями. */
export async function fetchAdminTables(): Promise<AdminTable[]> {
  return (await http.get<AdminTable[]>('/admin/tables')).data
}

export async function fetchAdminRows(table: string, query: AdminQuery): Promise<AdminPage<AdminRow>> {
  return (await http.get<AdminPage<AdminRow>>(`/admin/tables/${table}/rows`, { params: params(query) })).data
}

/** Новая запись: values — поля по имени колонки. */
export async function createAdminRow(table: string, values: Record<string, unknown>): Promise<AdminRow> {
  return (await http.post<AdminRow>(`/admin/tables/${table}/rows`, values)).data
}

export async function updateAdminRow(table: string, id: number, values: Record<string, unknown>): Promise<AdminRow> {
  return (await http.put<AdminRow>(`/admin/tables/${table}/rows/${id}`, values)).data
}

/** Удалить запись. Запись используется — ошибка 409 с текстом для пользователя. */
export async function deleteAdminRow(table: string, id: number): Promise<void> {
  await http.delete(`/admin/tables/${table}/rows/${id}`)
}

/** Варианты для поля-ссылки на таблицу ref. */
export async function fetchAdminOptions(ref: string): Promise<AdminOption[]> {
  return (await http.get<AdminOption[]>(`/admin/options/${ref}`)).data
}

// --- сотрудники

/** Сотрудник: строка списка и карточка. */
export interface AdminUser {
  id: number
  login: string
  /** ФИО полностью */
  fullName: string
  phone: string | null
  /** роли — id role_type (варианты — fetchAdminOptions('role_type')) */
  roles: number[]
  /** депо, действующее сейчас; null — нет */
  depot: number | null
  /** категория проводника; null — нет */
  categoryType: number | null
  disabled: boolean
  /** сменить пароль при входе */
  changePassword: boolean
}

/** Сотрудник из формы. password: у нового обязателен; у существующего пусто — пароль не меняется. */
export interface AdminUserDraft {
  login: string
  fullName: string
  phone: string
  roles: number[]
  depot: number | null
  categoryType: number | null
  disabled: boolean
  password: string
}

export async function fetchAdminUsers(query: AdminQuery): Promise<AdminPage<AdminUser>> {
  return (await http.get<AdminPage<AdminUser>>('/admin/users', { params: params(query) })).data
}

export async function createAdminUser(draft: AdminUserDraft): Promise<AdminUser> {
  return (await http.post<AdminUser>('/admin/users', draft)).data
}

export async function updateAdminUser(id: number, draft: AdminUserDraft): Promise<AdminUser> {
  return (await http.put<AdminUser>(`/admin/users/${id}`, draft)).data
}
