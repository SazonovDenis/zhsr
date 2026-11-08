import { t } from '@/boot/i18n'
import { RoleType } from '@/shared/constants/systemDicts'
import { currentUser, hasRole } from '@/shared/user'

/**
 * Почему у текущего пользователя нет прав на действие — подпись у неактивной кнопки на десктопе. Пусто — у
 * пользователя есть одна из ролей или он администратор (администратор может всё). Реактивно: зависит от currentUser.
 *
 * @param roles роли, которым действие доступно — константы RoleType
 * @param text  причина на русском, например «Только администратор»; переводится
 */
export function roleReason(roles: number[], text: string): string {
  const u = currentUser.value
  if (hasRole(u, RoleType.ADMIN) || roles.some((r) => hasRole(u, r))) return ''
  return t(text)
}
