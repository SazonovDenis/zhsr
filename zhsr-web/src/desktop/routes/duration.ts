import { t } from '@/boot/i18n'

/** Время станции маршрута «чч:мм»: часы от полуночи дня отправления, следующие сутки — больше 24 («26:55»). */
export function formatTime(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
}

/** Длительность в минутах — словами: «3 ч 42 м», «42 м», «3 ч». */
export function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return t('{m} м', { m })
  return m === 0 ? t('{h} ч', { h }) : t('{h} ч {m} м', { h, m })
}

/** Длительность в минутах — словами полностью: «23 ч 34 мин», «34 мин», «3 ч». */
export function formatHoursMin(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return t('{m} мин', { m })
  return m === 0 ? t('{h} ч', { h }) : t('{h} ч {m} мин', { h, m })
}

/** Длительность в минутах — часы и минуты: «0:34», «2:32», «19:05». */
export function formatHm(minutes: number): string {
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`
}

/** Время станции маршрута: прибытие и отправление, мин от полуночи дня отправления. */
interface Timed {
  arrivalMin: number | null
  departureMin: number | null
}

/** Когда поезд уходит со станции: отправление, у конечной станции — прибытие. */
export function leaveOf(point: Timed): number | null {
  return point.departureMin ?? point.arrivalMin
}

/** Время в пути от предыдущей станции, мин; у начальной станции 0. */
export function runOf(points: Timed[], index: number): number {
  const prev = points[index - 1]
  const arrival = points[index]?.arrivalMin
  const leave = prev ? leaveOf(prev) : null
  return arrival == null || leave == null ? 0 : arrival - leave
}

/** Стоянка, мин: отправление минус прибытие; у начальной и конечной станций и станций следования 0. */
export function stopOf(point: Timed): number {
  return point.arrivalMin == null || point.departureMin == null ? 0 : point.departureMin - point.arrivalMin
}

/** Время в пути по маршруту, мин: прибытие на конечную станцию минус отправление с начальной. */
export function totalMinutes(points: Timed[]): number {
  const first = points[0]?.departureMin
  const last = points[points.length - 1]?.arrivalMin
  return first == null || last == null ? 0 : Math.max(0, last - first)
}
