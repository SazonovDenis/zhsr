import { defineBoot } from '#q-app'

import { isLoginRequired, isPasswordChangeRequired } from '@/api/http'
import { notifyError } from '@/shared/errors'

/**
 * Необработанные ошибки — всплывашкой снизу, а не молча в консоль: ошибки компонентов (errorHandler Vue) и отклонённые
 * промисы, которые никто не ждал. Сервер требует сменить пароль (406) или войти (401) — не ошибка: переход на смену пароля или на вход уже идёт.
 */
export default defineBoot(({ app }) => {
  const handle = (e: unknown) => {
    console.error(e)
    if (!isPasswordChangeRequired(e) && !isLoginRequired(e)) notifyError(e)
  }
  app.config.errorHandler = handle
  window.addEventListener('unhandledrejection', (event) => handle(event.reason))
})
