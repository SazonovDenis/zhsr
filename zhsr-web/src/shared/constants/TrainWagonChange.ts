/**
 * Журнал поезда и участие вагона в поезде: код записи журнала (имя таблицы, как TrainJournalCode.java); значения train_wagon_change.change_type, kind и plan_status,
 * train_wagon.kind. Таблиц справочника нет: константы — как TrainWagonChangeType.java, TrainWagonKind.java и
 * TrainWagonPlanStatus.java на сервере.
 */
export const TrainJournalCode = {
  /** Прицепка или отцепка вагона */
  TRAIN_WAGON_CHANGE: 'train_wagon_change',
  /** Смена порядка вагонов */
  TRAIN_WAGON_SEQ: 'train_wagon_seq',
} as const

export const TrainWagonChangeType = {
  /** Прицепка: вагон входит в поезд — на станции или в пути */
  ATTACH: 1,
  /** Отцепка: вагон выходит из поезда — на станции или в пути */
  DETACH: 2,
} as const

export const TrainWagonKind = {
  /** План: у журнала — запланированная прицепка или отцепка; у участия — ожидаемый состав */
  PLAN: 1,
  /** Факт: у журнала — прицепка или отцепка, которая состоялась; у участия — история состава */
  FACT: 2,
} as const

export const TrainWagonPlanStatus = {
  /** Ожидает: факт по плану ещё не отмечен */
  WAITING: 1,
  /** Отработан: отмечен факт по плану */
  DONE: 2,
  /** Отменён: план не состоялся */
  CANCELLED: 3,
} as const
