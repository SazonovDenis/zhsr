import type { InjectionKey } from 'vue'

import type { TripRef } from '@/api/wagons'

/** Состояние выбора проводников диалога «Поездная бригада» для ячеек таблицы (provide/inject). */
export interface CrewPick {
  /** рейс диалога */
  tripId: number
  /** занят в пересекающемся рейсе: выбрать нельзя, вместо галочки замок */
  isBusy: (id: number) => boolean
  /** рейс, от которого сотрудник освободится к отправлению, и когда (ISO); null — свободен */
  busy: (id: number) => { trip: TripRef | null; until: string } | null
  isPicked: (id: number) => boolean
  toggle: (id: number) => void
  /** почему выбор недоступен; пусто — доступен */
  reason: () => string
}

export const CREW_PICK: InjectionKey<CrewPick> = Symbol('crewPick')
