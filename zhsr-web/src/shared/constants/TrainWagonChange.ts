/**
 * Прицепка и отцепка вагона: значения train_wagon_change.change_type и train_wagon_change.status. Таблиц справочника
 * нет: константы - как TrainWagonChangeType.java и TrainWagonChangeStatus.java на сервере.
 */
export const TrainWagonChangeType = {
  /** Прицепка: вагон входит в поезд - на станции или в пути */
  ATTACH: 1,
  /** Отцепка: вагон выходит из поезда - на станции или в пути */
  DETACH: 2,
} as const

export const TrainWagonChangeStatus = {
  /** План: изменение запланировано, журналы вагона не менялись */
  PLANNED: 1,
  /** Выполнено: факт отмечен */
  DONE: 2,
  /** Отменено: план изменения отменён */
  CANCELLED: 3,
} as const
