import { date } from 'quasar'

import type { PeriodPreset } from '@/shared/components/period/period'
import { presetDates } from '@/shared/components/period/period'

/** Выбор периода (PeriodPicker) в диаграммах: готовые периоды, кнопки и период по умолчанию. */

const today = new Date()
// неделя — 7 дней вместе с сегодняшним
const weekAgo = date.subtractFromDate(today, { days: 6 })

/** Кнопки периода; остальные готовые периоды — в «...». */
export const PERIODS = ['d1', 'd7', 'm1', 'prevMonth']

/** Готовый период от дня start по сегодня. */
function untilToday(value: string, label: string, start: Date): PeriodPreset {
  return { value, label, from: -date.getDateDiff(today, start, 'days'), to: 0 }
}

// встроенные периоды PeriodPicker и прошлый месяц: свои периоды заменяют встроенные целиком
export const PRESETS: PeriodPreset[] = [
  untilToday('d1', 'Сегодня', today),
  untilToday('d3', '3 дня', date.subtractFromDate(today, { days: 2 })),
  untilToday('d7', 'Неделя', weekAgo),
  untilToday('m1', 'Месяц', date.subtractFromDate(today, { months: 1 })),
  { value: 'prevMonth', label: 'Прошлый месяц', from: -1, to: -1, months: true },
  untilToday('mStart', 'С начала месяца', new Date(today.getFullYear(), today.getMonth(), 1)),
  untilToday('m3', '3 месяца', date.subtractFromDate(today, { months: 3 })),
  untilToday('m6', 'Полгода', date.subtractFromDate(today, { months: 6 })),
  untilToday('y', 'Год', date.subtractFromDate(today, { years: 1 })),
  untilToday('yStart', 'С начала года', new Date(today.getFullYear(), 0, 1)),
]

/** Период по умолчанию — неделя по сегодня: ISO-даты «2026-10-30». */
export function weekDates(): { from: string; to: string } {
  return presetDates(PRESETS.find((p) => p.value === 'd7')!)
}
