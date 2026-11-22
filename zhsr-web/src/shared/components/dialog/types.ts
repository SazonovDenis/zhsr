/**
 * Действие диалога: кнопка в подвале (actions у DialogFrame) или в заголовке (actions у DialogHeader).
 *
 * Что делает кнопка:
 * - задан exec — вызывается exec;
 * - задано только name — диалог отправляет событие action с этим именем;
 * - не задано ни то ни другое — кнопка закрывает диалог.
 */
export interface DialogAction {
  name?: string
  /** текст кнопки на русском; переводится при показе */
  title?: string
  /** иконка Quasar (material-icons) */
  icon?: string
  /** основное действие: выполняется по Enter, кнопка оформлена как основная */
  default?: boolean
  disabled?: boolean
  visible?: (action: DialogAction) => boolean
  exec?: (action: DialogAction) => void
  /** только в заголовке: кнопка справа от заголовка, иначе слева */
  right?: boolean
  /** только в подвале: кнопка у левого края, например «Отмена»; иначе справа */
  left?: boolean
}

/**
 * Высота диалога — постоянная, от высоты экрана в пределах min и max: содержимое не меняет высоту окна, лишнее
 * прокручивается в теле, недостающее остаётся пустым внизу. sm — подтверждение и одно-два поля; md — короткая форма;
 * lg — форма побольше или список; xl — таблица во всю высоту.
 */
export type DialogHeight = 'sm' | 'md' | 'lg' | 'xl'

/** Высота диалога: готовая (DialogHeight) или своя — значение CSS. */
export type DialogHeightValue = DialogHeight | (string & Record<never, never>)

/** Высоты диалога: clamp(min, доля экрана, max), не выше окна. */
export const DIALOG_HEIGHTS: Record<DialogHeight, string> = {
  sm: 'min(clamp(13rem, 30vh, 18rem), calc(100vh - 3rem))',
  md: 'min(clamp(20rem, 50vh, 30rem), calc(100vh - 3rem))',
  lg: 'min(clamp(28rem, 70vh, 42rem), calc(100vh - 3rem))',
  xl: 'min(clamp(34rem, 85vh, 52rem), calc(100vh - 3rem))',
}

/** Высота диалога: готовая (DialogHeight) или своя — значение CSS, например «min(19rem, calc(100vh - 3rem))». */
export function dialogHeight(height: DialogHeightValue): string {
  return height in DIALOG_HEIGHTS ? DIALOG_HEIGHTS[height as DialogHeight] : height
}

export function isActionVisible(action: DialogAction): boolean {
  return action.visible ? action.visible(action) : true
}
