import type { RouteLocationRaw } from 'vue-router'

import { RoleType } from '@/shared/constants/systemDicts'
import { currentUser, loadCurrentUser } from '@/shared/user'

/** Роли бригады: видят в аналитике только диаграммы с признаком crew. */
const CREW_ROLES: number[] = [RoleType.CONDUCTOR, RoleType.PEM, RoleType.LNP]

/** У пользователя только роли бригады: проводник, ПЭМ, ЛНП. Реактивно: зависит от currentUser. */
export function isCrewOnly(): boolean {
  return !currentUser.value?.roles.some((r) => !CREW_ROLES.includes(r))
}

/** Виден ли раздел «Аналитика» на телефоне: у пользователя есть роль. Реактивно: зависит от currentUser. */
export function canSeeAnalytics(): boolean {
  return !!currentUser.value?.roles.length
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
