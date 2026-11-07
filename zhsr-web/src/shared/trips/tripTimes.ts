import { onBeforeUnmount, ref } from 'vue'

import { t } from '@/boot/i18n'

/** Плановое прибытие: плановое отправление плюс время в пути, мин. ISO. */
export function plannedArrival(plannedDt: string, duration: number): string {
  return new Date(Date.parse(plannedDt) + duration * 60_000).toISOString()
}

/** Насколько факт разошёлся с планом: «опоздание 1 ч 5 мин», «раньше на 3 мин», «по плану». */
export function delayText(planned: string, actual: string): string {
  const minutes = Math.round((Date.parse(actual) - Date.parse(planned)) / 60_000)
  if (minutes === 0) return t('по плану')
  const abs = Math.abs(minutes)
  const span = abs >= 60 ? t('{h} ч {m} мин', { h: Math.floor(abs / 60), m: abs % 60 }) : t('{m} мин', { m: abs })
  return minutes > 0 ? t('опоздание {span}', { span }) : t('раньше на {span}', { span })
}

/** Сколько после отметки отправления или прибытия её можно исправить или отменить, мс (как на сервере). */
export const MARK_EDIT_MS = 10 * 60_000

/** Можно ли ещё исправить или отменить отметку, внесённую в markedDt; now — текущее время, мс. */
export function markEditable(markedDt: string | null | undefined, now: number): boolean {
  return !!markedDt && now < Date.parse(markedDt) + MARK_EDIT_MS
}

/** Текущее время, мс, — обновляется раз в 15 секунд: кнопки исправления отметки исчезают сами, когда срок вышел. */
export function useNow() {
  const now = ref(Date.now())
  const timer = setInterval(() => (now.value = Date.now()), 15_000)
  onBeforeUnmount(() => clearInterval(timer))
  return now
}
