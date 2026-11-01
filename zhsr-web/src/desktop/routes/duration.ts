/** Длительность в минутах — часы и минуты: «0:34», «2:32», «19:05». */
export function formatHm(minutes: number): string {
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`
}

/** Время в пути по маршруту, мин: перегоны и стоянки на промежуточных остановках. */
export function totalMinutes(points: { runDuration: number; stopDuration: number }[]): number {
  return points.reduce((sum, p, i) => sum + p.runDuration + (i > 0 && i < points.length - 1 ? p.stopDuration : 0), 0)
}
