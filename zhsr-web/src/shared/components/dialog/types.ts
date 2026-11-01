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
}

export function isActionVisible(action: DialogAction): boolean {
  return action.visible ? action.visible(action) : true
}
