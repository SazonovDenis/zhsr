import type { TrainCard } from '@/api/trains'
import type { TripRef } from '@/api/wagons'
import { TripStatus } from '@/shared/constants/systemDicts'

/**
 * Прошлые рейсы поезда — выполненные, сначала последний: для режима «Прошлое» экрана поезда. from — начало периода
 * (мс): рейс прибыл не раньше; null — все.
 */
export function pastTrips(card: TrainCard | null, from: number | null): TripRef[] {
  if (!card) return []
  return card.tripMarks
    .filter((m) => m.tripStatusType === TripStatus.DONE)
    .filter((m) => from === null || Date.parse(m.arrivedDt ?? m.arrival) >= from)
    .sort((a, b) => Date.parse(b.plannedDt) - Date.parse(a.plannedDt))
    .map((m) => m.trip)
}
