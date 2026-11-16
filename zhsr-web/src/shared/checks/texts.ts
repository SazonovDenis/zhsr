import type { Texts } from '@/api/checks'
import { i18n } from '@/boot/i18n'

/**
 * Название на языке интерфейса: казахское или английское, если задано, иначе русское. Реактивно: зависит от языка.
 */
export function textOf(texts: Texts | null | undefined): string {
  if (!texts) return ''
  const locale = i18n.global.locale.value
  if (locale === 'kk' && texts.textKk) return texts.textKk
  if (locale === 'en' && texts.textEn) return texts.textEn
  return texts.textRu
}

/** Число без лишних нулей для показа: 20.000 → «20», 0.070 → «0.07»; null — пусто. */
export function numText(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : String(Number(value))
}
