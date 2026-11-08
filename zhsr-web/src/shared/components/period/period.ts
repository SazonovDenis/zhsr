import { date } from 'quasar'

/** Свой готовый период PeriodPicker: границы — в днях (или календарных месяцах) от сегодня. */
export interface PeriodPreset {
  value: string
  /** подпись на русском; переводится при показе */
  label: string
  /** начало: дней от сегодня, включительно; при months — месяцев от текущего, с первого числа */
  from: number
  /** конец: дней от сегодня, включительно; при months — месяцев от текущего, по последнее число */
  to: number
  /** границы в календарных месяцах: «текущий месяц» — 0..0, «прошлый месяц» — -1..-1 */
  months?: boolean
}

/** Даты готового периода на сегодня — ISO-даты «2026-10-30». */
export function presetDates(preset: PeriodPreset): { from: string; to: string } {
  const iso = (d: Date) => date.formatDate(d, 'YYYY-MM-DD')
  if (preset.months) {
    const now = new Date()
    return {
      from: iso(new Date(now.getFullYear(), now.getMonth() + preset.from, 1)),
      to: iso(new Date(now.getFullYear(), now.getMonth() + preset.to + 1, 0)),
    }
  }
  return {
    from: iso(date.addToDate(new Date(), { days: preset.from })),
    to: iso(date.addToDate(new Date(), { days: preset.to })),
  }
}
