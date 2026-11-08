/**
 * Константы и записи системных справочников: значения меняются только вместе с кодом, поэтому фронт не
 * загружает их с сервера (DictManager берёт записи отсюда). Привязка к значению - по id через константы:
 * TripStatus.IN_PROGRESS.
 *
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/*.tsv - не править вручную.
 */

/** Статус акта: id act_status_type */
export const ActStatus = {
  /** Ожидает приёма - акт приёма-передачи сдан, приём не отмечен; расположение вагонов не меняется */
  PENDING: 1,
  /** Проведён - акт действует; акт приёма-передачи принят: расположение вагонов изменено */
  DONE: 2,
  /** Отказ - принимающая сторона отказала в приёме с причиной; расположение вагонов не меняется */
  REJECTED: 3,
  /** Отменён - акт отменён актом отмены act_cancellation; записи журналов, открытые актом, удалены */
  CANCELLED: 4,
} as const

/** Категория проводника: id category_type */
export const ConductorCategory = {
  /** Стажёр - проходит стажировку под руководством проводника */
  TRAINEE: 1,
  /** Проводник 2 разряда - ЕТКС: проводник пассажирского вагона 2-го разряда */
  GRADE_2: 2,
  /** Проводник 3 разряда - ЕТКС: проводник пассажирского вагона 3-го разряда (поезда дальнего следования) */
  GRADE_3: 3,
  /** Старший проводник - вагоны повышенной категории, координирует проводников */
  SENIOR: 4,
} as const

/** Отцепка или прицепка: id coupling_type */
export const CouplingType = {
  /** Отцепка */
  DETACH: 1,
  /** Прицепка */
  ATTACH: 2,
} as const

/** Должность члена бригады на рейсе: id crew_duty_type */
export const CrewDuty = {
  /** Проводник - может быть закреплён за вагоном */
  CONDUCTOR: 1,
  /** Начальник поезда */
  LNP: 2,
  /** Поездной электромеханик */
  PEM: 3,
} as const

/** Вид расположения вагона: id location_type */
export const LocationType = {
  /** Станция */
  STATION: 1,
  /** Депо */
  DEPOT: 2,
  /** В рейсе */
  TRIP: 3,
  /** Парк отстоя */
  YARD: 4,
} as const

/** Статус заявки на ремонт: id request_status_type */
export const RequestStatus = {
  /** Новая - ждёт назначения исполнителя */
  NEW: 1,
  /** Назначена - исполнитель назначен */
  ASSIGNED: 2,
  /** В работе - исполнитель взял заявку в работу */
  IN_PROGRESS: 3,
  /** Выполнена - работа выполнена, ждёт приёмки */
  DONE: 4,
  /** Закрыта - работа принята */
  CLOSED: 5,
  /** Отклонена - получатель счёл заявку необоснованной */
  REJECTED: 6,
  /** Отложена до ТО - выполнить сейчас невозможно: мастер депо или исполнитель отложил работу до ТО */
  DEFERRED: 7,
  /** Отменена - автор отозвал заявку */
  CANCELLED: 8,
} as const

/** Роль сотрудника: id role_type */
export const RoleType = {
  /** Администратор компании */
  ADMIN: 1,
  /** Проводник */
  CONDUCTOR: 2,
  /** Поездной электромеханик (ПЭМ) */
  PEM: 3,
  /** Начальник поезда (ЛНП) */
  LNP: 4,
  /** Мастер депо */
  MASTER: 5,
  /** Приёмщик */
  RECEIVER: 6,
  /** Клининг - учётная запись подрядчика */
  CLEANING: 7,
  /** Руководитель */
  MANAGER: 8,
  /** Экипировщик - МСИ, съёмное оборудование, посуда, медицинские принадлежности */
  EQUIPPER: 9,
  /** Инструктор поездных бригад - замещает нормировщика */
  INSTRUCTOR: 10,
  /** Инструктор-нормировщик (нарядный стол) */
  SCHEDULER: 11,
  /** Планировщик вагонов - нет в zholserik; вагоны, маршруты, рейсы, состав, ЛНП и ПЭМ (tz.actors.md) */
  WAGON_PLANNER: 12,
  /** Эксплуатационщик */
  OPERATOR: 13,
  /** Планировщик проводников - нет в zholserik; проводники рейса (tz.actors.md) */
  CREW_PLANNER: 14,
  /** Медик */
  MEDIC: 15,
} as const

/** Служба: id service */
export const ServiceType = {
  /** Техническая - ПЭМ, мастер депо */
  TECHNICAL: 1,
  /** Клининг */
  CLEANING: 2,
  /** Экипировка */
  EQUIPMENT: 3,
  /** Инструкторская - инструктор поездных бригад, инструктор-нормировщик, медик */
  INSTRUCTOR: 4,
  /** Эксплуатация - эксплуатационщик */
  OPERATION: 5,
} as const

/** Критичность неисправности: id severity_type */
export const Severity = {
  /** Низкая */
  LOW: 1,
  /** Средняя - по умолчанию */
  MEDIUM: 2,
  /** Высокая */
  HIGH: 3,
  /** Критическая */
  CRITICAL: 4,
} as const

/** Вид перевозки: id transport_type */
export const TransportType = {
  /** Пассажирская - по умолчанию */
  PASSENGER: 1,
  /** Грузовая */
  FREIGHT: 2,
} as const

/** Состояние рейса: id trip_status_type */
export const TripStatus = {
  /** План - по умолчанию */
  PLANNED: 1,
  /** Выполняется */
  IN_PROGRESS: 2,
  /** Выполнен */
  DONE: 3,
  /** Отменён */
  CANCELLED: 4,
} as const

/** Состояние вагона: id wagon_status_type */
export const WagonStatus = {
  /** В простое - по умолчанию */
  IN_IDLE: 1,
  /** В пути */
  IN_TRIP: 2,
  /** В ремонте - ставится при ТОР */
  IN_REPAIR: 3,
  /** Резерв - подбирается в состав, если не хватает годных */
  RESERVE: 4,
  /** Отставлен */
  OUT_OF_SERVICE: 5,
} as const

/** Вид работ: id work_type */
export const WorkType = {
  /** ТО */
  TO: 1,
  /** ТОР */
  TOR: 2,
  /** ТР-1 */
  TR1: 3,
  /** ТР-2 */
  TR2: 4,
  /** ДР */
  DR: 5,
  /** КР */
  KR: 6,
  /** ЕТР */
  ETR: 7,
  /** Отстой - без ремонта */
  STORAGE: 8,
} as const

/** Записи системных справочников фронта по имени справочника DictManager: id, text (на русском) и ссылки. */
export const SYSTEM_DICT_RECORDS = {
  // crew_duty_type
  CrewDuty: [
    { id: 1, text: 'Проводник' },
    { id: 2, text: 'Начальник поезда' },
    { id: 3, text: 'Поездной электромеханик' },
  ],
  // location_type
  LocationKind: [
    { id: 1, text: 'Станция' },
    { id: 2, text: 'Депо' },
    { id: 3, text: 'В рейсе' },
    { id: 4, text: 'Парк отстоя' },
  ],
  // request_status_type
  RequestStatus: [
    { id: 1, text: 'Новая' },
    { id: 2, text: 'Назначена' },
    { id: 3, text: 'В работе' },
    { id: 4, text: 'Выполнена' },
    { id: 5, text: 'Закрыта' },
    { id: 6, text: 'Отклонена' },
    { id: 7, text: 'Отложена до ТО' },
    { id: 8, text: 'Отменена' },
  ],
  // role_type
  Role: [
    { id: 1, text: 'Администратор компании', service: null },
    { id: 2, text: 'Проводник', service: null },
    { id: 3, text: 'Поездной электромеханик (ПЭМ)', service: 1 },
    { id: 4, text: 'Начальник поезда (ЛНП)', service: null },
    { id: 5, text: 'Мастер депо', service: 1 },
    { id: 6, text: 'Приёмщик', service: null },
    { id: 7, text: 'Клининг', service: 2 },
    { id: 8, text: 'Руководитель', service: null },
    { id: 9, text: 'Экипировщик', service: 3 },
    { id: 10, text: 'Инструктор поездных бригад', service: 4 },
    { id: 11, text: 'Инструктор-нормировщик (нарядный стол)', service: 4 },
    { id: 12, text: 'Планировщик вагонов', service: null },
    { id: 13, text: 'Эксплуатационщик', service: 5 },
    { id: 14, text: 'Планировщик проводников', service: null },
    { id: 15, text: 'Медик', service: 4 },
  ],
  // service
  Service: [
    { id: 1, text: 'Техническая' },
    { id: 2, text: 'Клининг' },
    { id: 3, text: 'Экипировка' },
    { id: 4, text: 'Инструкторская' },
    { id: 5, text: 'Эксплуатация' },
  ],
  // severity_type
  Severity: [
    { id: 1, text: 'Низкая' },
    { id: 2, text: 'Средняя' },
    { id: 3, text: 'Высокая' },
    { id: 4, text: 'Критическая' },
  ],
  // transport_type
  TransportType: [
    { id: 1, text: 'Пассажирская' },
    { id: 2, text: 'Грузовая' },
  ],
  // trip_status_type
  TripState: [
    { id: 1, text: 'План' },
    { id: 2, text: 'Выполняется' },
    { id: 3, text: 'Выполнен' },
    { id: 4, text: 'Отменён' },
  ],
  // wagon_status_type
  WagonState: [
    { id: 1, text: 'В простое' },
    { id: 2, text: 'В пути' },
    { id: 3, text: 'В ремонте' },
    { id: 4, text: 'Резерв' },
    { id: 5, text: 'Отставлен' },
  ],
  // work_type
  WorkType: [
    { id: 1, text: 'ТО' },
    { id: 2, text: 'ТОР' },
    { id: 3, text: 'ТР-1' },
    { id: 4, text: 'ТР-2' },
    { id: 5, text: 'ДР' },
    { id: 6, text: 'КР' },
    { id: 7, text: 'ЕТР' },
    { id: 8, text: 'Отстой' },
  ],
}
