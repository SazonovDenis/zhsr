import { i18n, t } from '@/boot/i18n'
import { noBreak } from '@/shared/format'

function number(v: number): string {
  return new Intl.NumberFormat(i18n.global.locale.value, { maximumFractionDigits: 1 }).format(v)
}

/** Длительность в минутах текстом: «15 мин», «4 ч», «1,5 сут». */
export function duration(minutes: number): string {
  if (minutes < 60) return noBreak(t('{m} мин', { m: Math.round(minutes) }))
  if (minutes < 1440) return noBreak(t('{h} ч', { h: number(minutes / 60) }))
  return noBreak(t('{n} сут', { n: number(minutes / 1440) }))
}
