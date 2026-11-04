import { date } from 'quasar'

import type { PeriodPreset } from '@/shared/components/period/period'
import { presetDates } from '@/shared/components/period/period'
import { TripStatus } from '@/shared/constants/systemDicts'

/**
 * Связь фильтров «Период» и «Состояние» списка рейсов. Состояния определяют сторону времени: план и выполняется —
 * будущее, выполнен — прошлое, вместе или без выбора — обе стороны. От стороны зависят варианты периода и период по
 * умолчанию; период, целиком лежащий в другой стороне, меняет состояния.
 */

export type PeriodSide = 'future' | 'past' | 'both'

const TODAY: PeriodPreset = { value: 'today', label: 'Сегодня', from: 0, to: 0 }
const PAST_3: PeriodPreset = { value: 'past3', label: 'Прошедшие 3 дня', from: -3, to: 0 }
const NEXT_3: PeriodPreset = { value: 'next3', label: 'Следующие 3 дня', from: 0, to: 3 }
const PAST_WEEK: PeriodPreset = { value: 'pastWeek', label: 'Прошедшая неделя', from: -7, to: 0 }
const NEXT_WEEK: PeriodPreset = { value: 'nextWeek', label: 'Следующая неделя', from: 0, to: 7 }
const NEAR: PeriodPreset = { value: 'near', label: 'Ближайшие', from: -7, to: 7 }
const PAST_MONTH: PeriodPreset = { value: 'pastMonth', label: 'Прошедший месяц', from: -30, to: 0 }
const NEXT_MONTH: PeriodPreset = { value: 'nextMonth', label: 'Следующий месяц', from: 0, to: 30 }

// варианты — от мелких к широким
const PRESETS: Record<PeriodSide, PeriodPreset[]> = {
  future: [TODAY, NEXT_3, NEXT_WEEK, NEXT_MONTH],
  past: [TODAY, PAST_3, PAST_WEEK, PAST_MONTH],
  both: [TODAY, PAST_3, NEXT_3, PAST_WEEK, NEXT_WEEK, NEAR, PAST_MONTH, NEXT_MONTH],
}

const DEFAULTS: Record<PeriodSide, PeriodPreset> = { future: NEXT_WEEK, past: PAST_WEEK, both: NEAR }

/** Состояния по умолчанию: рейсы, которые ещё идут или предстоят. */
export const FUTURE_STATES: number[] = [TripStatus.PLANNED, TripStatus.IN_PROGRESS]

export function statesSide(states: number[]): PeriodSide {
  const future = FUTURE_STATES.some((s) => states.includes(s))
  const past = states.includes(TripStatus.DONE)
  if (future && !past) return 'future'
  if (past && !future) return 'past'
  return 'both'
}

export function sidePresets(side: PeriodSide): PeriodPreset[] {
  return PRESETS[side]
}

/** Период по умолчанию для стороны: ISO-даты. */
export function sidePeriod(side: PeriodSide): { from: string; to: string } {
  return presetDates(DEFAULTS[side])
}

/**
 * Состояния после выбора периода: период целиком в другой стороне меняет состояния; иначе — null, состояния остаются.
 *
 * @param from ISO-дата начала
 * @param to   ISO-дата конца
 */
export function statesForPeriod(from: string | null, to: string | null, states: number[]): number[] | null {
  if (!from || !to) return null
  const today = date.formatDate(new Date(), 'YYYY-MM-DD')
  const side = statesSide(states)
  if (to <= today && from < today && side === 'future') return [TripStatus.DONE]
  if (from >= today && to > today && side === 'past') return [...FUTURE_STATES]
  return null
}
