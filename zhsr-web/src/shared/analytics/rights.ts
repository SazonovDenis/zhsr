import type { RouteLocationRaw } from 'vue-router'

import { RoleType } from '@/shared/constants/systemDicts'
import { currentUser, loadCurrentUser } from '@/shared/user'

/** Роли, которым раздел «Аналитика» на телефоне не показывается. */
const CREW_ROLES: number[] = [RoleType.CONDUCTOR, RoleType.PEM, RoleType.LNP]

/**
 * Виден ли раздел «Аналитика» на телефоне: у пользователя есть роль, кроме проводника, ПЭМ и ЛНП. Реактивно: зависит
 * от currentUser.
 */
export function canSeeAnalytics(): boolean {
  return !!currentUser.value?.roles.some((r) => !CREW_ROLES.includes(r))
}

/** Переход в раздел на телефоне: раздел пользователю не виден — на главную. */
export async function requireAnalytics(): Promise<true | RouteLocationRaw> {
  try {
    await loadCurrentUser()
  } catch {
    // ошибку загрузки пользователя показывает каркас мобильной части (PwaLayout)
  }
  return canSeeAnalytics() ? true : { name: 'pwa-home' }
}
