import { t } from '@/boot/i18n'

/**
 * Фраза с жирными частями и ссылками на рейс: одно описание — для разметки (PhraseView) и для простого текста
 * (phraseText). Ключ — русский текст с подстановками, переводится целиком; подстановки — такие же части.
 */
export type Phrase =
  | string
  | { bold: string }
  /** ссылка на рейс: trip — рейс, train — поезд рейса, text — текст ссылки */
  | { trip: number; train: number; text: string }
  | { key: string; params: Record<string, Phrase> }

/** Фраза простым текстом — те же ключи через t(). */
export function phraseText(phrase: Phrase): string {
  if (typeof phrase === 'string') return phrase
  if ('bold' in phrase) return phrase.bold
  if ('trip' in phrase) return phrase.text
  const params = Object.fromEntries(Object.entries(phrase.params).map(([name, part]) => [name, phraseText(part)]))
  return t(phrase.key, params)
}
