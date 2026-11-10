/**
 * Порядок полей администрирования: одно место для таблицы и диалога. Правило по умолчанию: название, код, остальные
 * поля в порядке сервера. Таблице с особым порядком — запись в ADMIN_FIELD_ORDER: перечисленные поля идут первыми в
 * этом порядке, за ними по правилу все остальные (новое поле с сервера не теряется). Ключ — имя таблицы; поля — как их
 * называет таблица: колонки БД у общего редактора, поля AdminUser у сотрудников.
 */
export const ADMIN_FIELD_ORDER: Record<string, string[]> = {
  // названия на всех языках — рядом
  depot: ['name_ru', 'name_kk', 'name_en', 'code'],
  // сотрудники: свои поля, правило их не знает
  usr: ['fullName', 'login', 'phone', 'roles', 'depot', 'categoryType', 'disabled', 'password'],
}

const NAME_FIELDS = ['name', 'name_ru', 'text', 'text_ru']
const CODE_FIELD = 'code'

/** Место поля в таблице: чем меньше, тем раньше. У полей вне записи и вне правила место одно — порядок сервера. */
export function fieldRank(table: string, field: string): number {
  const order = ADMIN_FIELD_ORDER[table] ?? []
  const index = order.indexOf(field)
  if (index !== -1) return index
  if (NAME_FIELDS.includes(field)) return order.length
  return field === CODE_FIELD ? order.length + 1 : order.length + 2
}

/** Поля по порядку таблицы; поля с одним местом остаются в исходном порядке. */
export function sortFields<T>(table: string, fields: T[], nameOf: (field: T) => string): T[] {
  return [...fields].sort((a, b) => fieldRank(table, nameOf(a)) - fieldRank(table, nameOf(b)))
}
