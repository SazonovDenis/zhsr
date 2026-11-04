/**
 * Реестр справочников, которыми управляет DictManager.
 *
 * В DictManager попадают только справочники: небольшие, без периода действия, одинаковые для всех ролей. Объекты с
 * периодом действия (маршруты, станции графа на дату) через DictManager не идут.
 *
 * Справочники двух видов:
 * - системные (system) — значения меняются только вместе с кодом: записи зашиты в код (shared/constants:
 *   systemDicts.ts из zhsr-database/gen-constants.py, RouteKind.ts), с сервера не загружаются. Привязка к значению — по id
 *   через константы: TripStatus.IN_PROGRESS. Оформление (цвета) — dictColors.ts;
 * - пополняемые — их ведёт администрирование: загружаются с сервера (api/dict.ts), обновляются по команде сервера.
 *   У записи есть code: привязка к значению, когда она нужна, — по коду, не по id.
 *
 * translate — как переводится текстовое поле записи:
 * - 'i18n' — текст на русском переводится через t() вместе с интерфейсом (системные справочники);
 * - 'multilang' — сервер отдаёт все переводы сразу: { ru, kk, en }; показывается текущий язык, при отсутствии — ru;
 * - 'none' — не переводится (собственные имена: станции; то, что ввёл администратор).
 *
 * offline — справочник нужен без сети: он хранится и в памяти, и в IndexedDB, загружается при старте приложения.
 */

export type DictTranslate = 'i18n' | 'multilang' | 'none'

export interface DictDef {
  translate: DictTranslate
  /** системный справочник: записи зашиты в код */
  system?: boolean
  offline?: boolean
}

export const dictDefs = {
  // системные
  WagonState: { translate: 'i18n', system: true },
  LocationKind: { translate: 'i18n', system: true },
  TripState: { translate: 'i18n', system: true },
  RouteKind: { translate: 'i18n', system: true },
  TransportType: { translate: 'i18n', system: true },
  CrewDuty: { translate: 'i18n', system: true },
  WorkType: { translate: 'i18n', system: true },
  // роли сотрудников; service — служба роли (id справочника Service), может не быть
  Role: { translate: 'i18n', system: true },
  RequestStatus: { translate: 'i18n', system: true },
  Severity: { translate: 'i18n', system: true },
  Service: { translate: 'i18n', system: true },
  // категория неисправности; service — служба-исполнитель категории
  FaultCategory: { translate: 'i18n', system: true },
  // пополняемые
  WagonType: { translate: 'none' },
  WagonModel: { translate: 'none' },
  // station — станция депо
  Depot: { translate: 'multilang' },
  // hub — узловая станция (со своим депо): с неё может начинаться и на ней заканчиваться маршрут
  Station: { translate: 'none', offline: true },
  // station — станция парка отстоя
  Yard: { translate: 'none' },
  // группа неисправностей; sortOrder — порядок показа; нужна мобильной части без сети — подача заявки
  FaultGroup: { translate: 'multilang', offline: true },
  // вид неисправности: faultGroupType, faultCategoryType, severityType — критичность по умолчанию, service — служба-исполнитель (может не
  // быть), disabled — отключён: не предлагается при подаче заявки
  FaultType: { translate: 'multilang', offline: true },
} satisfies Record<string, DictDef>

export type DictName = keyof typeof dictDefs

/** id записи справочника: число, как id в БД. */
export type DictId = number

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
