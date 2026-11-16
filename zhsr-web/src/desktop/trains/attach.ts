import type { TrainChangeBody } from '@/api/trains'

/** Остановка рейса для прицепки и отцепки: route_station.id, станция и время стоянки по расписанию (ISO). */
export interface AttachStop {
  routeStation: number
  station: number
  time: string
}

/**
 * Выбор в диалогах прицепки и отцепки: fact — факт или план; time — факт: фактическое время, план вне рейса:
 * плановое время, план в рейсе: null (время стоянки); station — вне рейса: станция; routeStation — остановка рейса.
 */
export interface ChangeChoice {
  fact: boolean
  time: string | null
  station: number | null
  routeStation: number | null
}

/** Выбор диалога «Прицепить вагоны»: номера вагонов и как прицепить. */
export interface AttachChoice extends ChangeChoice {
  numbers: string[]
}

/** Тело запроса прицепки или отцепки из выбора диалога; trip — рейс, если прицепка для рейса. */
export function changeBody(numbers: string[], choice: ChangeChoice, trip: number | null): TrainChangeBody {
  return {
    wagons: numbers,
    fact: choice.fact,
    time: choice.time,
    station: choice.station,
    trip: choice.routeStation === null ? null : trip,
    routeStation: choice.routeStation,
    comment: '',
    responsibleUsr: null,
  }
}

/** Остановки рейса для плана: все, кроме станции прибытия. */
export function tripStops(trip: { routeStations: number[]; stops: number[]; stopTimes: string[] }): AttachStop[] {
  return trip.routeStations
    .map((routeStation, i) => ({ routeStation, station: trip.stops[i]!, time: trip.stopTimes[i]! }))
    .slice(0, -1)
}
