import { CheckObject } from '@/api/checks'
import { RoleType } from '@/shared/constants/systemDicts'
import { roleReason } from '@/shared/roleReason'
import { currentUser, hasRole } from '@/shared/user'

/**
 * Права на чек-листы и описи экипировки (tz.actors.md) — та же матрица, что на сервере (CheckRights): кто проводит
 * проверку по объекту проверки, кто составляет опись. Администратор может всё. Функции отдают причину, почему нельзя
 * (переведённую); пусто — можно. Реактивно: зависит от currentUser.
 */

/** Объект проверки (CheckObject) — роли, которые проводят проверку (Ф-ЧЕК-03). */
export const CHECK_BY_OBJECT: Record<number, number[]> = {
  [CheckObject.WAGON]: [
    RoleType.CONDUCTOR,
    RoleType.LNP,
    RoleType.PEM,
    RoleType.MASTER,
    RoleType.RECEIVER,
    RoleType.INSTRUCTOR,
  ],
  [CheckObject.USR]: [RoleType.MEDIC, RoleType.INSTRUCTOR],
  [CheckObject.TRIP]: [RoleType.LNP, RoleType.INSTRUCTOR],
}

/** Роли, которые составляют опись экипировки (Ф-ЭКП-02). */
export const EQUIP_ROLES = [
  RoleType.EQUIPPER,
  RoleType.CONDUCTOR,
  RoleType.LNP,
  RoleType.MASTER,
  RoleType.RECEIVER,
  RoleType.INSTRUCTOR,
]

/** Почему нельзя провести проверку такого объекта; пусто — можно. */
export function checkReason(checkObject: number): string {
  const text =
    checkObject === CheckObject.USR
      ? 'Проверку сотрудника проводят медик и инструктор'
      : checkObject === CheckObject.TRIP
        ? 'Проверку рейса проводят ЛНП и инструктор'
        : 'Проверку вагона проводят проводник, ЛНП, ПЭМ, мастер депо, приёмщик, инструктор'
  return roleReason(CHECK_BY_OBJECT[checkObject] ?? [], text)
}

/** Почему нельзя провести ни одной проверки; пусто — хотя бы одну можно. */
export function anyCheckReason(): string {
  const all = [...new Set(Object.values(CHECK_BY_OBJECT).flat())]
  return roleReason(all, 'Проверки проводят проводник, ЛНП, ПЭМ, мастер депо, приёмщик, инструктор, медик')
}

/** Почему нельзя составить опись экипировки; пусто — можно. */
export function equipReason(): string {
  return roleReason(
    EQUIP_ROLES,
    'Опись составляют мастер экипировки, проводник, ЛНП, мастер депо, приёмщик, инструктор',
  )
}

/** Срок исправления чек-листа и описи автором после последнего сохранения, мс — как на сервере. */
export const LIST_EDIT_PERIOD = 10 * 60 * 1000

/**
 * Можно ли исправить чек-лист или опись: автор или администратор в течение 10 минут после последнего сохранения
 * (completedDt). now — текущее время, мс (useNow): кнопка «Внести изменения» исчезает сама, когда срок вышел.
 */
export function listEditable(list: { createdByUsr: { id: number }; completedDt: string }, now: number): boolean {
  const user = currentUser.value
  if (!user || (user.id !== list.createdByUsr.id && !hasRole(user, RoleType.ADMIN))) return false
  return now < Date.parse(list.completedDt) + LIST_EDIT_PERIOD
}
