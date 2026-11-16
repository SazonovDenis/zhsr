import type { Texts } from '@/api/checks'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import { openDialog } from '@/shared/components/dialog/openDialog'
import type { Loadable } from '@/shared/loadable'
import SpecDialog from './SpecDialog.vue'

/** Значения формы SpecDialog: поле — значение (строка, число, признак, id или массив id; пусто — null). */
export type SpecValues = Record<string, unknown>

/**
 * Поле формы SpecDialog.
 *
 * kind: text — строка; number — число (дробное через точку или запятую); bool — «да/нет»; single — одно значение из
 * choices; multi — несколько значений из choices; fault — неисправность деревом «служба → группа → неисправность»
 * (FaultPicker).
 */
export interface SpecField {
  /** имя поля тела запроса — как колонка БД в camelCase */
  name: string
  /** подпись на русском; переводится при показе */
  label: string
  kind: 'text' | 'number' | 'bool' | 'single' | 'multi' | 'fault'
  required?: boolean
  /** варианты single и multi */
  choices?: ChoiceItem[]
  /** загрузка вариантов: спиннер и ошибка — в самом списке */
  source?: Loadable | null
  /** текст пустого поля на русском */
  placeholder?: string
  /** поле во всю ширину формы */
  wide?: boolean
  /** поле видно при текущих значениях формы; не задано — всегда */
  visible?: (values: SpecValues) => boolean
}

/** Поля названия на трёх языках, порядка и отключения — общие для записей администрирования чек-листов и описей. */
export const TEXT_FIELDS: SpecField[] = [
  { name: 'textRu', label: 'Название на русском', kind: 'text', required: true, wide: true },
  { name: 'textKk', label: 'Название на казахском', kind: 'text', wide: true },
  { name: 'textEn', label: 'Название на английском', kind: 'text', wide: true },
]

/**
 * Поле отключения записи. Порядка показа в форме нет: его меняют кнопки перестановки в списке; значение sortOrder из
 * initial (textValues) уходит в save как есть.
 */
export const ORDER_FIELDS: SpecField[] = [{ name: 'disabled', label: 'Отключено', kind: 'bool' }]

/** Начальные значения названий, порядка и отключения: из записи или пустые для новой. */
export function textValues(rec: (Texts & { sortOrder: number; disabled: boolean }) | null, sortOrder = 0): SpecValues {
  return {
    textRu: rec?.textRu ?? '',
    textKk: rec?.textKk ?? '',
    textEn: rec?.textEn ?? '',
    sortOrder: rec?.sortOrder ?? sortOrder,
    disabled: rec?.disabled ?? false,
  }
}

/** Следующий порядок показа после записей: на 10 больше наибольшего. */
export function nextOrder(recs: { sortOrder: number }[]): number {
  return recs.reduce((max, r) => Math.max(max, r.sortOrder), 0) + 10
}

/**
 * Открыть SpecDialog. Результат — ответ save, 'deleted' или undefined, если диалог закрыли.
 */
export function openSpecDialog<T>(props: {
  title: string
  okText?: string
  hint?: string
  width?: string
  fields: SpecField[]
  initial: SpecValues
  save: (values: SpecValues) => Promise<T>
  remove?: (() => Promise<void>) | null
}): Promise<T | 'deleted' | undefined> {
  return openDialog<T | 'deleted'>(SpecDialog, props)
}
