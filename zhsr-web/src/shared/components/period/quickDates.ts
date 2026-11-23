import { date } from 'quasar'

/** Своя готовая дата места применения: ISO-дата «2026-10-31» и подпись, уже переведённая. */
export interface DatePreset {
  value: string
  label: string
}

/**
 * Быстрые варианты даты для DatePicker. Набор вариантов задаёт место применения: для планового отправления рейса
 * нет смысла во «Вчера», для ввода вагона в эксплуатацию — в «Завтра».
 */
export type QuickDate =
  | 'yesterday'
  | 'today'
  | 'todayPlus1'
  | 'todayPlus2'
  | 'todayPlus3'
  | 'inWeek'
  | 'monthStart'
  | 'nextMonthStart'
  | 'monthEnd'
  | 'yearStart'
  | 'yearEnd'
  | 'now'
  | 'prevMonthEnd'
  | 'prevPrevMonthEnd'

/** Подписи вариантов на русском; переводятся при показе. */
export const QUICK_DATE_LABELS: Record<QuickDate, string> = {
  yesterday: 'Вчера',
  today: 'Сегодня',
  todayPlus1: 'Завтра',
  todayPlus2: 'Послезавтра',
  todayPlus3: 'Через 2 дня',
  inWeek: 'Через неделю',
  monthStart: 'Начало месяца',
  nextMonthStart: 'Начало следующего месяца',
  monthEnd: 'Конец месяца',
  yearStart: 'Начало года',
  yearEnd: 'Конец года',
  now: 'Сейчас',
  prevMonthEnd: 'Прошлый месяц',
  prevPrevMonthEnd: 'Позапрошлый месяц',
}

/** Дата варианта относительно сегодняшнего дня. */
export function quickDateValue(code: QuickDate, today = new Date()): Date {
  const y = today.getFullYear()
  const m = today.getMonth()
  const d = new Date(y, m, today.getDate())
  switch (code) {
    case 'yesterday':
      return date.addToDate(d, { days: -1 })
    case 'today':
    case 'now':
      return d
    case 'todayPlus1':
      return date.addToDate(d, { days: 1 })
    case 'todayPlus2':
      return date.addToDate(d, { days: 2 })
    case 'todayPlus3':
      return date.addToDate(d, { days: 3 })
    case 'inWeek':
      return date.addToDate(d, { days: 7 })
    case 'monthStart':
      return new Date(y, m, 1)
    case 'nextMonthStart':
      return new Date(y, m + 1, 1)
    case 'prevMonthEnd':
      return new Date(y, m, 0)
    case 'prevPrevMonthEnd':
      return new Date(y, m - 1, 0)
    case 'monthEnd':
      return new Date(y, m + 1, 0)
    case 'yearStart':
      return new Date(y, 0, 1)
    case 'yearEnd':
      return new Date(y, 11, 31)
  }
}
