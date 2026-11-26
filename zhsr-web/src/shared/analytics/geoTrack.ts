import type { GeoRoute, GeoTrip } from '@/api/analytics'

/** Точка во времени: минуты от начала периода и два значения — долгота и широта либо километр и отклонение. */
export type TimedPoint = [number, number, number]

/** Поезд в заданном месте карты. */
export interface ExpectedTrain {
  /** широта и долгота */
  at: [number, number]
  /** направление хода, градусы; null — поезд не в пути */
  bearing: number | null
}

/** Отставание и опережение, минут, при которых цвет линии предельный. */
export const LATE_LIMIT = 60
export const EARLY_LIMIT = 30

/** Между точками с большим промежутком, минут, положение не вычисляется. */
const GAP_MINUTES = 30

// опорные цвета: отставание — по графику — опережение
const LATE = [0xca, 0x2b, 0x2b]
const ON_TIME = [0x66, 0x66, 0x66]
const EARLY = [0x35, 0x38, 0xcc]

/** Направление хода — по следующим стольким километрам линии. */
const BEARING_KM = 2

/** Число ступеней цвета в каждую сторону от «по графику». */
const STEPS = 8

/** Ступени легенды, реже ступеней карты: от отставания (STEPS) через 0 (по графику) к опережению (−STEPS). */
export const LEGEND_STEPS = [3, 2, 1, 0, -1, -2, -3].map((n) => (n * STEPS) / 3)

/** Ступень цвета: от −STEPS (опережение) через 0 (по графику) до STEPS (отставание). */
export function delayStep(delay: number): number {
  const share = delay > 0 ? Math.min(1, delay / LATE_LIMIT) : -Math.min(1, -delay / EARLY_LIMIT)
  return Math.round(share * STEPS)
}

export function stepColor(step: number): string {
  const end = step > 0 ? LATE : EARLY
  const share = Math.abs(step) / STEPS
  return `rgb(${ON_TIME.map((c, i) => Math.round(c + (end[i]! - c) * share)).join(' ')})`
}

/** Номер последней точки не позже minute; -1 — все точки позже. */
function lastIndex(points: TimedPoint[], minute: number): number {
  let lo = 0
  let hi = points.length
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    if (points[mid]![0] <= minute) lo = mid + 1
    else hi = mid
  }
  return lo - 1
}

function between(a: TimedPoint, b: TimedPoint, minute: number): [number, number] {
  const part = b[0] > a[0] ? (minute - a[0]) / (b[0] - a[0]) : 0
  return [a[1] + (b[1] - a[1]) * part, a[2] + (b[2] - a[2]) * part]
}

/**
 * Значения на момент: между соседними точками — на прямой между ними, при промежутке больше GAP_MINUTES — в
 * предыдущей точке. null — первая точка позже момента.
 */
export function valueAt(points: TimedPoint[], minute: number): [number, number] | null {
  const i = lastIndex(points, minute)
  const a = points[i]
  if (!a) return null
  const b = points[i + 1]
  if (b && b[0] - a[0] <= GAP_MINUTES) return between(a, b, minute)
  return [a[1], a[2]]
}

/** Километр маршрута по расписанию через minute минут после отправления с начальной станции. */
export function scheduleKm(route: GeoRoute, minute: number): number {
  let previous = route.stops[0]
  if (!previous || minute <= previous.departureMin) return previous?.km ?? 0
  for (const stop of route.stops.slice(1)) {
    if (minute < stop.arrivalMin) {
      const part = (minute - previous.departureMin) / (stop.arrivalMin - previous.departureMin)
      return previous.km + (stop.km - previous.km) * part
    }
    if (minute <= stop.departureMin) return stop.km
    previous = stop
  }
  return previous.km
}

/** Место линии маршрута по километру: широта и долгота. */
export function routePlace(route: GeoRoute, km: number): [number, number] | null {
  const points = route.points
  if (points.length === 0) return null
  let lo = 0
  let hi = points.length - 1
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    if (points[mid]![2] < km) lo = mid + 1
    else hi = mid
  }
  const b = points[lo]!
  const a = points[Math.max(0, lo - 1)]!
  const part = b[2] > a[2] ? Math.min(1, Math.max(0, (km - a[2]) / (b[2] - a[2]))) : 0
  return [a[1] + (b[1] - a[1]) * part, a[0] + (b[0] - a[0]) * part]
}

/** Направление хода в месте линии: градусы от севера по часовой стрелке; в конце линии — как перед ним. */
export function routeBearing(route: GeoRoute, km: number): number {
  const length = route.points[route.points.length - 1]?.[2] ?? 0
  const from = Math.max(0, Math.min(km, length - BEARING_KM))
  const a = routePlace(route, from)
  const b = routePlace(route, from + BEARING_KM)
  if (!a || !b) return 0
  const dx = (b[1] - a[1]) * Math.cos((a[0] * Math.PI) / 180)
  return (Math.atan2(dx, b[0] - a[0]) * 180) / Math.PI
}

/** Линия маршрута между километрами: точки — широта и долгота. */
export function routePart(route: GeoRoute, fromKm: number, toKm: number): [number, number][] {
  const start = routePlace(route, fromKm)
  const end = routePlace(route, toKm)
  if (!start || !end) return []
  const inner = route.points.filter((p) => p[2] > fromKm && p[2] < toKm).map((p): [number, number] => [p[1], p[0]])
  return [start, ...inner, end]
}

/** Часть хода рейса одного цвета. */
export interface TrackPiece {
  step: number
  from: number
  to: number
  fromKm: number
  toKm: number
}

/** Части хода рейса с начала периода: соседние участки одной ступени цвета — одна часть. */
export function trackPieces(trip: GeoTrip): TrackPiece[] {
  const res: TrackPiece[] = []
  for (let i = 1; i < trip.track.length; i++) {
    let a = trip.track[i - 1]!
    const b = trip.track[i]!
    if (b[0] <= 0) continue
    if (a[0] < 0) a = [0, ...between(a, b, 0)]
    const step = delayStep((a[2] + b[2]) / 2)
    const last = res[res.length - 1]
    if (last && last.step === step) {
      last.to = b[0]
      last.toKm = b[1]
    } else {
      res.push({ step, from: a[0], to: b[0], fromKm: a[1], toKm: b[1] })
    }
  }
  return res
}
