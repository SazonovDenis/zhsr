/**
 * Реестр справочников, которыми управляет DictManager.
 *
 * В DictManager попадают только справочники: небольшие, без периода действия, одинаковые для всех ролей. Объекты с
 * периодом действия (маршруты, станции графа на дату) через DictManager не идут.
 *
 * translate — как переводится текстовое поле записи:
 * - 'i18n' — сервер отдаёт текст на русском, он переводится через t() вместе с интерфейсом (системные перечисления:
 *   состояния вагона и рейса);
 * - 'multilang' — сервер отдаёт все переводы сразу: { ru, kk, en }; показывается текущий язык, при отсутствии — ru
 *   (справочники, которые ведёт администрирование);
 * - 'none' — не переводится (собственные имена: станции).
 *
 * offline — справочник нужен без сети: он хранится и в памяти, и в IndexedDB, загружается при старте приложения.
 */

export type DictTranslate = 'i18n' | 'multilang' | 'none'

export interface DictDef {
  translate: DictTranslate
  offline?: boolean
}

export const dictDefs = {
  WagonState: { translate: 'i18n' },
  WagonType: { translate: 'i18n' },
  WagonModel: { translate: 'i18n' },
  LocationKind: { translate: 'i18n' },
  TripState: { translate: 'i18n' },
  RouteKind: { translate: 'i18n' },
  Depot: { translate: 'multilang' },
  Station: { translate: 'none', offline: true },
  Yard: { translate: 'none' },
  // роли сотрудников; service — служба роли (id справочника Service), может не быть
  Role: { translate: 'i18n', offline: true },
  // заявки на ремонт: нужны мобильной части без сети — подача заявки и показ списка
  RequestStatus: { translate: 'i18n', offline: true },
  Severity: { translate: 'i18n', offline: true },
  Service: { translate: 'i18n', offline: true },
  // категория неисправности; service — служба-исполнитель категории, color — цвет метки категории
  FaultCategory: { translate: 'i18n', offline: true },
  // группа неисправностей; sort — порядок показа
  FaultGroup: { translate: 'multilang', offline: true },
  // вид неисправности: group, category, severity — критичность по умолчанию, service — служба-исполнитель (может не быть)
  FaultType: { translate: 'multilang', offline: true },
} satisfies Record<string, DictDef>

export type DictName = keyof typeof dictDefs

export type DictId = string | number

/** Текст на всех языках: { ru, kk, en }. */
export type MultiText = Partial<Record<'ru' | 'kk' | 'en', string>>

/** Запись справочника: id, текст и любые другие поля. */
export interface DictRec {
  id: DictId
  text?: string | MultiText
  [field: string]: unknown
}

/** Справочник с сервера: записи и версия; версия меняется при любом изменении справочника. */
export interface DictData {
  version: string
  records: DictRec[]
}
