import type { InjectionKey } from 'vue'

/** Состояние выбора проводников диалога «Поездная бригада» для ячеек таблицы (provide/inject). */
export interface CrewPick {
  isPicked: (id: number) => boolean
  toggle: (id: number) => void
  /** почему выбор недоступен; пусто — доступен */
  reason: () => string
}

export const CREW_PICK: InjectionKey<CrewPick> = Symbol('crewPick')
