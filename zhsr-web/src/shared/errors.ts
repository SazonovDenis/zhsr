import { Notify } from 'quasar'

import { ApiError } from '@/api/http'
import { t } from '@/boot/i18n'

/**
 * Показ ошибок. Ошибки не подавляются: ошибка загрузки списка или карточки показывается на месте списка (ErrorBox), а
 * остальные — системные, действия, необработанные — всплывашкой снизу (notifyError). Под текстом ошибки запроса —
 * бледной второй строкой запрос, который упал.
 */

/** Ошибка для показа: текст (переведённый) и запрос, который упал; пусто — ошибка не от запроса. */
export interface ShownError {
  text: string
  request: string
}

/** Ошибка для показа. */
export function shownError(e: unknown): ShownError {
  return { text: errorText(e), request: e instanceof ApiError ? e.request : '' }
}

/** Текст ошибки для пользователя, переведённый. */
export function errorText(e: unknown): string {
  if (e instanceof Error) return t(e.message)
  return t(String(e))
}

/**
 * Всплывашка с ошибкой внизу экрана — в цветах блока ошибки ErrorBox (color и textColor zs-error, app.scss), с иконкой
 * предупреждения; запрос — бледной второй строкой. Клик по всплывашке сразу её закрывает.
 */
export function notifyError(e: unknown): void {
  const { text, request } = shownError(e)
  const dismiss = Notify.create({
    position: 'bottom',
    color: 'zs-error',
    textColor: 'zs-error',
    icon: 'warning',
    classes: 'zs-error-notify',
    message: text,
    ...(request ? { caption: request } : {}),
    attrs: { onClick: () => dismiss() },
  })
}

/** Ошибка проверки формы для показа: текст (уже переведённый), без запроса. */
export function formError(text: string): ShownError {
  return { text, request: '' }
}
