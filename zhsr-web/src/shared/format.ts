const pad = (n: number) => String(n).padStart(2, '0')

/** Дата и время для показа: «28.10 21:00»; год показывается, только если он не текущий. Пусто — прочерк. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  const year = d.getFullYear() !== new Date().getFullYear() ? `.${d.getFullYear()}` : ''
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}${year} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * Момент в ISO с часовым поясом браузера: «2026-10-29T07:05:00+05:00». Сервер принимает время только с поясом.
 */
export function isoDateTime(d: Date): string {
  const offset = -d.getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const abs = Math.abs(offset)
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}` +
    `${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
  )
}

/**
 * Начало календарного дня в поясе браузера, ISO с поясом. day — ISO-дата «2026-10-30»; addDays — сдвиг в днях: начало
 * следующего дня — граница «по день включительно».
 */
export function dayStart(day: string, addDays = 0): string {
  const [y, m, d] = day.split('-').map(Number)
  return isoDateTime(new Date(y!, m! - 1, d! + addDays))
}
