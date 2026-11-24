import type { TripRef } from '@/api/wagons'
import { formatDateTime } from '@/shared/format'

/** Рейс для показа: «002 • Алматы — Астана». */
export function tripRefText(trip: TripRef): string {
  return `${trip.trainNumber} • ${trip.name}`
}

/** Рейс для показа с датой: поезд ходит регулярно, поэтому номер и маршрут не отличают один рейс от другого. */
export function tripRefDatedText(trip: TripRef): string {
  return `${tripRefText(trip)} • ${formatDateTime(trip.plannedDt)}`
}
