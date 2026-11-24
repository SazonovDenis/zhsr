import type { InjectionKey } from 'vue'

import type { TrainWagon } from '@/api/trains'

/** Рейс поезда, выбранный на экране поезда: состав показывается на его отправление. */
export interface TripMoment {
  id: number
  /** рейс словами: «002 • Алматы — Астана • 22.11 10:00» */
  text: string
  /** номер поезда маршрута: «655/656» */
  number: string
  /** момент состава, ISO: фактическое отправление, если было, иначе плановое */
  at: string
  /** рейс отправлен: состав на отправление — по факту; иначе — ожидаемый по плану */
  fact: boolean
  /** станция отправления — id справочника Station; null — у маршрута нет остановок */
  departureStation: number | null
}

/** Передача вагона на станцию отправления рейса — для ячейки состава (provide/inject). */
export interface TripTransfer {
  /** вагон до отправления не на станции отправления */
  away: (w: TrainWagon) => boolean
  /** почему передачу не оформить; пусто — можно */
  reason: (w: TrainWagon) => string
  open: (w: TrainWagon) => void
}

export const TRIP_TRANSFER: InjectionKey<TripTransfer> = Symbol('tripTransfer')

/** Действия над планами вагона в ячейке «По плану» состава — как в журнале (provide/inject). */
export interface MomentPlans {
  /** выбрать запись плана журнала */
  select: (id: number) => void
  /** выбрать рейс поезда */
  pickTrip: (id: number) => void
}

export const MOMENT_PLANS: InjectionKey<MomentPlans> = Symbol('momentPlans')
