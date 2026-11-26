import type { InjectionKey } from 'vue'

import type { EquipListShortage } from '@/api/equip'
import type { TrainWagon, WagonProblem } from '@/api/trains'
import type { WagonNorm } from '@/shared/trips/crewNorm'

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
  /** неготовность вагонов рейса — расчёт сервера по планам */
  problems: WagonProblem[]
  /** норма проводников едущих вагонов рейса */
  norm: WagonNorm[]
}

/** Неготовности вагонов рейса и передача вагона на станцию отправления — для ячейки состава (provide/inject). */
export interface TripTransfer {
  /** неготовности вагона */
  problems: (w: TrainWagon) => WagonProblem[]
  /** почему передачу не оформить; пусто — можно */
  reason: (w: TrainWagon) => string
  open: (w: TrainWagon) => void
}

export const TRIP_TRANSFER: InjectionKey<TripTransfer> = Symbol('tripTransfer')

/** Опись экипировки вагона состава — для ячейки готовности (provide/inject). */
export interface TripEquip {
  /** последние описи вагона по шаблонам; пустой список — опись не проводилась; null — ещё не загружены */
  lists: (wagon: number) => EquipListShortage[] | null
  /** открыть опись в разделе «Описи» */
  open: (list: EquipListShortage, number: string) => void
}

export const TRIP_EQUIP: InjectionKey<TripEquip> = Symbol('tripEquip')

/** Норма проводников вагона состава — для ячейки готовности (provide/inject). */
export interface TripConductors {
  /** у пассажирского вагона не выполнена норма проводников */
  notOk: (w: TrainWagon) => boolean
  /** почему ответственных не назначить; пусто — можно */
  reason: () => string
  /** назначить ответственных за вагон */
  assign: (w: TrainWagon) => void
}

export const TRIP_CONDUCTORS: InjectionKey<TripConductors> = Symbol('tripConductors')

/** Действия над планами вагона в ячейке «Запланировано» состава — как в журнале (provide/inject). */
export interface MomentPlans {
  /** выбрать запись плана журнала (changeKey) */
  select: (key: string) => void
  /** выбрать рейс поезда */
  pickTrip: (id: number) => void
}

export const MOMENT_PLANS: InjectionKey<MomentPlans> = Symbol('momentPlans')
