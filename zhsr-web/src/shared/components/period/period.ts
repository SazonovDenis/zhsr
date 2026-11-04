import { date } from 'quasar'

/** Свой готовый период PeriodPicker: границы — в днях от сегодня. */
export interface PeriodPreset {
  value: string
  /** подпись на русском; переводится при показе */
  label: string
  /** начало: дней от сегодня, включительно */
  from: number
  /** конец: дней от сегодня, включительно */
  to: number
}

/** Даты готового периода на сегодня — ISO-даты «2026-10-30». */
export function presetDates(preset: PeriodPreset): { from: string; to: string } {
  const day = (days: number) => date.formatDate(date.addToDate(new Date(), { days }), 'YYYY-MM-DD')
  return { from: day(preset.from), to: day(preset.to) }
}
