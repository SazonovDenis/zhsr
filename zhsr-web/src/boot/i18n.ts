import { defineBoot } from '#q-app'
import type { PathValue } from 'vue-i18n'
import { createI18n } from 'vue-i18n'

import messages from '@/i18n'

export type MessageLanguages = keyof typeof messages

/**
 * Ключ сообщения — сам русский текст: t('Ввести вагон'). Ключи не придумываются.
 *
 * - Русский текст показывается как есть: в ru.json только формы множественного числа.
 * - kk.json и en.json содержат переводы с русским текстом в качестве ключа: { "Вагоны": "Vagons" }.
 * - Ключ ищется целиком, без разбора по точкам: точка в тексте не делает ключ вложенным.
 * - Текст ключа разбирается как сообщение vue-i18n, поэтому подстановки работают и без перевода:
 *   t('Показано {n} из {total}', { n, total }). Символы { } @ | в тексте экранируются: {'@'}.
 */
export const i18n = createI18n({
  locale: 'ru',
  fallbackLocale: 'ru',
  legacy: false,
  messages,
  // ключ ищется в сообщениях целиком
  messageResolver: (obj, path) => (obj as Record<string, PathValue>)[path] ?? null,
  // перевода нет — показывается сам ключ, разобранный как сообщение
  fallbackFormat: true,
  missingWarn: false,
  fallbackWarn: false,
  pluralRules: { ru: ruPlural },
})

/** Перевод вне компонентов: в описаниях колонок, меню, справочниках. */
export const t = i18n.global.t

/**
 * Выбор формы множественного числа для русского: "{n} вагон | {n} вагона | {n} вагонов".
 * Нулевой формы нет: 0 вагонов — третья форма.
 */
function ruPlural(choice: number, choicesLength: number): number {
  if (choicesLength < 3) return choice === 1 ? 0 : 1
  const n10 = choice % 10
  const n100 = choice % 100
  if (n10 === 1 && n100 !== 11) return 0
  if (n10 >= 2 && n10 <= 4 && (n100 < 12 || n100 > 14)) return 1
  return 2
}

export default defineBoot(({ app }) => {
  app.use(i18n)
})
