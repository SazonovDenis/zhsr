import { shallowRef } from 'vue'

import type { CurrentUser } from '@/api/session'
import { fetchCurrentUser, logout } from '@/api/session'
import { clearCached, loadCached } from '@/shared/offline/cache'

/**
 * Текущий пользователь для всего приложения: роли, депо, текущий рейс (см. CurrentUser). Загружается один раз
 * (loadCurrentUser); без связи берётся копия, сохранённая на телефоне при прошлой загрузке.
 */
export const currentUser = shallowRef<CurrentUser | null>(null)

let loading: Promise<CurrentUser> | null = null

/** Пользователь: уже загруженный или загрузить. force — загрузить заново (например, после восстановления связи). */
export function loadCurrentUser(force = false): Promise<CurrentUser> {
  if (currentUser.value && !force) return Promise.resolve(currentUser.value)
  loading ??= loadCached('user', fetchCurrentUser)
    .then(({ data }) => (currentUser.value = data))
    .finally(() => (loading = null))
  return loading
}

/** Выйти: завершить сеанс на сервере и забыть пользователя и копии его данных на телефоне. */
export async function logoutUser(): Promise<void> {
  await logout()
  currentUser.value = null
  await clearCached()
}

/** Есть ли у пользователя роль — id справочника Role. */
export function hasRole(user: CurrentUser | null, role: string): boolean {
  return !!user?.roles.includes(role)
}
