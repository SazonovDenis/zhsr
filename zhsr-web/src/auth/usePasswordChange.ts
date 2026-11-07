import { ref } from 'vue'

import { changePassword } from '@/api/session'
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { loadCurrentUser } from '@/shared/user'

/**
 * Поля и сохранение формы смены пароля — общее для страницы (пароль обязан сменить) и диалога из меню пользователя.
 * Ошибка формы — в error: проверки (formError) или запроса (shownError, запрос второй строкой).
 */
export function usePasswordChange() {
  const oldPassword = ref('')
  const newPassword = ref('')
  const repeatPassword = ref('')
  const running = ref(false)
  const error = ref<ShownError | null>(null)

  /** Проверить поля и сменить пароль. false — пароль не сменён, причина в error. */
  async function save(): Promise<boolean> {
    if (!oldPassword.value || !newPassword.value) {
      error.value = formError(t('Заполните все поля'))
      return false
    }
    if (newPassword.value !== repeatPassword.value) {
      error.value = formError(t('Новый пароль и повтор не совпадают'))
      return false
    }
    error.value = null
    running.value = true
    try {
      await changePassword(oldPassword.value, newPassword.value)
      // после обязательной смены пароля приложение ещё не сверяло локальную БД с сервером
      await loadCurrentUser(true)
      return true
    } catch (e) {
      error.value = shownError(e)
      return false
    } finally {
      running.value = false
    }
  }

  return { oldPassword, newPassword, repeatPassword, running, error, save }
}
