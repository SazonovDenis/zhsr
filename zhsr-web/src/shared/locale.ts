import { Lang } from 'quasar'
import langEn from 'quasar/lang/en-US'
import langKk from 'quasar/lang/kk'
import langRu from 'quasar/lang/ru'

import type { MessageLanguages } from '@/boot/i18n'
import { i18n } from '@/boot/i18n'

/**
 * Язык интерфейса: тексты приложения (vue-i18n) и встроенные тексты компонентов Quasar (календарь, таблицы).
 */

export interface LocaleOption {
  value: MessageLanguages
  /** название языка на самом этом языке — переводить его не нужно */
  label: string
  short: string
}

export const LOCALES: LocaleOption[] = [
  { value: 'ru', label: 'Русский', short: 'RU' },
  { value: 'kk', label: 'Қазақша', short: 'KK' },
  { value: 'en', label: 'English', short: 'EN' },
]

const QUASAR_LANGS = { ru: langRu, kk: langKk, en: langEn }

export function setLocale(locale: MessageLanguages) {
  i18n.global.locale.value = locale
  // пакеты языков Quasar и тип Lang.set расходятся только в необязательных полях (exactOptionalPropertyTypes)
  Lang.set(QUASAR_LANGS[locale] as Parameters<typeof Lang.set>[0])
  document.documentElement.lang = locale
}
