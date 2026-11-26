/**
 * Слой журнала ответственных за вагон: значения wagon_responsible.kind. Таблицы справочника нет: константы — как
 * WagonResponsibleKind.java на сервере.
 */
export const WagonResponsibleKind = {
  /** План */
  PLAN: 1,
  /** Факт */
  FACT: 2,
} as const
