import { ref } from 'vue'

import { t } from '@/boot/i18n'
import { formatDateTime, noBreak } from '@/shared/format'

/** Текущее время, мс; обновляется раз в минуту — для подписей «сколько уже длится» во всех ячейках сразу. */
export const nowMinute = ref(Date.now())
setInterval(() => (nowMinute.value = Date.now()), 60_000)

/**
 * Длительность словами: «25 мин», «8 час 25 мин», «3 дн 4 час»; от 10 дней — без часов: «12 дн». Нулевые часы и минуты
 * не пишутся: «14 час», «3 дн». Отрицательная — как ноль.
 */
export function spanText(ms: number): string {
  const minutes = Math.max(0, Math.floor(ms / 60_000))
  const days = Math.floor(minutes / 1440)
  const hours = Math.floor((minutes % 1440) / 60)
  const rest = minutes % 60
  if (days >= 10 || (days > 0 && hours === 0)) return noBreak(t('{d} дн', { d: days }))
  if (days > 0) return noBreak(t('{d} дн {h} час', { d: days, h: hours }))
  if (hours > 0 && rest === 0) return noBreak(t('{h} час', { h: hours }))
  if (hours > 0) return noBreak(t('{h} час {m} мин', { h: hours, m: rest }))
  return noBreak(t('{m} мин', { m: minutes }))
}

/** С какого времени и сколько уже длится: «с 25.10 01:05, 8 час 25 мин». now — текущее время, мс. */
export function sinceText(iso: string, now: number): string {
  return t('с {date}, {span}', { date: formatDateTime(iso), span: spanText(now - Date.parse(iso)) })
}
