import { i18n, t } from '@/boot/i18n'

const pad = (n: number) => String(n).padStart(2, '0')

/**
 * Дата и время для показа: «28.10 21:00»; год показывается, только если он не текущий. Пусто — прочерк. Между датой и
 * временем — неразрывный пробел: дата не отделяется от времени переносом строки.
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  const year = d.getFullYear() !== new Date().getFullYear() ? `.${d.getFullYear()}` : ''
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}${year}\u00a0${pad(d.getHours())}:${pad(d.getMinutes())}`
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

/** Округлить часы до двух знаков после запятой. */
export function roundHours(hours: number): number {
  return Math.round(hours * 100) / 100
}

/** Часы числом, не больше двух знаков после запятой: «2,5»; null — прочерк. */
export function formatHours(hours: number | null): string {
  if (hours === null) return '—'
  return new Intl.NumberFormat(i18n.global.locale.value, { maximumFractionDigits: 2 }).format(hours)
}

/** Пробелы неразрывные: длительность «4 час 37 мин» не рвётся переносом строки. */
export function noBreak(text: string): string {
  return text.replace(/ /g, '\u00a0')
}

/** Дробные часы — часы и минуты: 2.5 → «2:30». */
export function formatHoursMinutes(hours: number): string {
  const minutes = Math.round(hours * 60)
  return `${Math.floor(minutes / 60)}:${pad(minutes % 60)}`
}

/** Сравнить тексты для сортировки по языку интерфейса; числа внутри текста — по величине. */
export function compareText(a: string, b: string): number {
  return collator(i18n.global.locale.value).compare(a, b)
}

const collators = new Map<string, Intl.Collator>()

function collator(locale: string): Intl.Collator {
  let res = collators.get(locale)
  if (!res) collators.set(locale, (res = new Intl.Collator(locale, { numeric: true })))
  return res
}

/** Целое число с разбивкой на тысячи пробелом: 1234567 → «1 234 567». */
export function formatThousands(value: number): string {
  return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ' ')
}

/** Число вагонов с согласованным словом на языке интерфейса: 1 → «1 вагон», 3 → «3 вагона», 5 → «5 вагонов». */
export function formatWagons(n: number): string {
  return t('{n} вагонов', n)
}
