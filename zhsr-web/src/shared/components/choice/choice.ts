import { t } from '@/boot/i18n'

/**
 * Общее для контролов выбора CbButton, CbSegment и CbList.
 *
 * values: [{ id, text?, disabled? }] — id обязателен, text показывается (иначе id) и переводится при показе.
 * disabled: значение показывается, но выбрать или снять его нельзя. Если такое значение уже пришло в v-model, оно так
 * и остаётся выбранным — контрол сам ничего не снимает.
 * v-model повторяет режим контрола: при single — id выбранного значения или null, иначе массив id.
 * change передаёт эффективные значения (item.value вместо id) — всегда массив, даже при single: одно выбранное
 * значение может разворачиваться в несколько.
 */

export type ChoiceId = string | number

export interface ChoiceItem {
  id: ChoiceId
  /** текст на русском; переводится при показе */
  text?: string
  /** показать, но не давать выбрать */
  disabled?: boolean
  /** эффективное значение вместо id: одно или несколько */
  value?: unknown
  /** свободное значение, которого нет в values (CbList, allowCustom) */
  custom?: boolean
  /** число рядом с подписью, например сколько вагонов в этом состоянии (CbButton) */
  count?: number
  /** данные вызывающего для своего вида строки (слоты option и value у CbList) */
  data?: Record<string, unknown>
  /** дополнительный текст для поиска в CbList, не показывается: например, все станции маршрута */
  search?: string
}

export type ChoiceModel = ChoiceId | ChoiceId[] | null

/** Текст значения до перевода. */
export function labelOf(item: ChoiceItem): string {
  return item.text ?? String(item.id)
}

/** Текст значения, который видит пользователь. */
export function displayOf(item: ChoiceItem): string {
  return t(labelOf(item))
}

export function isDisabled(item: ChoiceItem | undefined): boolean {
  return item?.disabled === true
}

/** Эффективные значения варианта: item.value (или [item.value]), иначе [item.id]. */
export function effectiveOf(item: ChoiceItem): unknown[] {
  const v = item.value
  if (v === undefined || v === null) return [item.id]
  return Array.isArray(v) ? (v as unknown[]) : [v]
}

/** modelValue как массив id. */
export function modelIds(model: ChoiceModel | undefined): ChoiceId[] {
  if (Array.isArray(model)) return model
  if (model === null || model === undefined) return []
  return [model]
}

/** Выбранное в форме v-model: при single — id или null, иначе массив id. */
export function modelOf(selected: ChoiceItem[], single: boolean): ChoiceModel {
  if (single) return selected[0]?.id ?? null
  return selected.map((s) => s.id)
}

/** Совпадение со строкой поиска: по исходному тексту (он же ключ перевода) и по переведённому. */
export function matchesQuery(item: ChoiceItem, query: string): boolean {
  return labelOf(item).toLowerCase().includes(query) || displayOf(item).toLowerCase().includes(query)
}

/**
 * Ближайший индекс, на который можно встать курсором, шагая от from в направлении step: через недоступные значения
 * курсор перешагивает. Если дальше в эту сторону выбирать нечего — остаёмся на месте.
 */
export function nextEnabledIndex(items: ChoiceItem[], from: number, step: 1 | -1): number {
  let index = from + step
  while (index >= 0 && index < items.length) {
    if (!isDisabled(items[index])) return index
    index += step
  }
  return from
}
