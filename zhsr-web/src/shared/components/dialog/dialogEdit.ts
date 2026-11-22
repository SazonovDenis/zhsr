import type { InjectionKey } from 'vue'
import { inject } from 'vue'

/**
 * Пользователь изменил значение в поле диалога: FormDialog убирает ошибку. Контролы, у которых значение меняется не
 * вводом текста (выбор в списке, переключатель, календарь), вызывают её сами; ввод текста FormDialog ловит по событию
 * input. Вне диалога — ничего не делает.
 */
export const DIALOG_EDIT: InjectionKey<() => void> = Symbol('dialogEdit')

/** Сообщить диалогу, что значение изменено пользователем. Вызывать в setup контрола. */
export function useDialogEdit(): () => void {
  const edit = inject(DIALOG_EDIT, null)
  return () => edit?.()
}
