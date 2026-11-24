import type { TrainCard, TrainChangeBody } from '@/api/trains'
import type { TripOption } from '@/api/trips'
import { fetchTripCrew, fetchTripOptions } from '@/api/trips'
import type { TripRef } from '@/api/wagons'
import { TripStatus } from '@/shared/constants/systemDicts'

/** Остановка рейса для прицепки и отцепки: route_station.id, станция и время стоянки по расписанию (ISO). */
export interface AttachStop {
  routeStation: number
  station: number
  time: string
}

/** Рейс поезда в статусе «план» или «выполняется» и его остановки (без станции прибытия). */
export interface StopGroup {
  trip: TripRef
  /** рейс в статусе «выполняется»: факт — только на его остановке в пути */
  inProgress: boolean
  stops: AttachStop[]
  /** период рейса по расписанию, ISO: отправление и прибытие */
  from: string
  to: string
  /** рейс для богатого выбора (TripOptionView); null — сервер его не отдал */
  option: TripOption | null
}

/**
 * Выбор в окнах прицепки и отцепки: fact — факт или план; time — время (план на остановке — время стоянки); station —
 * вне рейса: станция; routeStation — остановка рейса; seq — прицепка: позиция первого вагона, null — в конец;
 * comment — основания, примечания. Рейс сервер находит сам по поезду и остановке.
 */
export interface ChangeChoice {
  fact: boolean
  time: string | null
  station: number | null
  routeStation: number | null
  seq: number | null
  comment: string
}

/** Выбор окна «Прицепить вагоны»: id вагонов и как прицепить. */
export interface AttachChoice extends ChangeChoice {
  wagons: number[]
}

/** Тело запроса прицепки или отцепки из выбора окна. */
export function changeBody(wagons: number[], choice: ChangeChoice): TrainChangeBody {
  return {
    wagons,
    fact: choice.fact,
    time: choice.time,
    station: choice.station,
    routeStation: choice.routeStation,
    seq: choice.seq,
    comment: choice.comment,
    responsibleUsrs: [],
  }
}

/** Остановки рейса для прицепки и отцепки: все, кроме станции прибытия. */
export function tripStops(trip: { routeStations: number[]; stops: number[]; stopTimes: string[] }): AttachStop[] {
  return trip.routeStations
    .map((routeStation, i) => ({ routeStation, station: trip.stops[i]!, time: trip.stopTimes[i]! }))
    .slice(0, -1)
}

/**
 * Рейсы поезда в статусе «план» и «выполняется» с остановками — для выбора места в окнах прицепки и отцепки; у рейсов
 * в статусе «план» — ещё вид для богатого выбора (варианты рейсов, как у фильтра «Рейс» списка вагонов).
 */
export async function loadStopGroups(train: TrainCard): Promise<StopGroup[]> {
  if (train.trips.length === 0) return []
  const [crews, options] = await Promise.all([
    Promise.all(train.trips.map((tr) => fetchTripCrew(tr.id))),
    fetchTripOptions({ route: null, station: null, from: null, to: null, states: [TripStatus.PLANNED] }),
  ])
  const byId = new Map(options.map((o) => [o.id, o]))
  return crews.map((c) => ({
    trip: train.trips.find((tr) => tr.id === c.trip.id)!,
    inProgress: c.trip.tripStatusType === TripStatus.IN_PROGRESS,
    stops: tripStops(c.trip),
    from: c.trip.stopTimes[0]!,
    to: c.trip.stopTimes[c.trip.stopTimes.length - 1]!,
    option: byId.get(c.trip.id) ?? null,
  }))
}
