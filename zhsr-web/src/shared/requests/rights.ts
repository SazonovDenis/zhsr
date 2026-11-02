import type { RepairRequest, RequestAction } from '@/api/requests'
import type { CurrentUser } from '@/api/session'

/**
 * Права пользователя на заявку на ремонт (Ф-ЗАЯ-02, Ф-ЗАЯ-03, Ф-ЗАЯ-07): какие действия показать. Решает клиент по
 * данным пользователя (роли, службы, депо, текущий рейс и должность в нём) и полям заявки (автор, исполнитель, где
 * вагон). Сервер проверяет права сам; здесь — только что показать.
 */

/** Статусы, в которых заявка закончена: действий по ней нет. */
export const FINAL_STATUSES = ['CLOSED', 'REJECTED', 'CANCELLED']

/** Открытые статусы — для отбора «открытые»; выполненная заявка ещё ждёт приёмки, поэтому тоже открыта. */
export const OPEN_STATUSES = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'DONE', 'DEFERRED']

/** Сколько минут после подачи автор может исправить заявку (Ф-ЗАЯ-07). */
const EDIT_MINUTES = 10

export interface ActionDef {
  /** текст кнопки на русском; переводится при показе */
  title: string
  /** вид кнопки: основная, обычная или опасная (красная) */
  kind: 'primary' | 'secondary' | 'danger'
  /** подсказка поля комментария на русском; null — комментарий не нужен */
  comment: string | null
}

/** Действия над заявкой в порядке показа кнопок. */
export const ACTIONS: Record<RequestAction, ActionDef> = {
  TAKE: { title: 'Взять в работу', kind: 'primary', comment: null },
  COMPLETE: { title: 'Выполнено', kind: 'primary', comment: 'Что сделано — попадёт в журнал ВУ-8' },
  ACCEPT: { title: 'Принять работу', kind: 'primary', comment: null },
  RETURN: { title: 'Вернуть на доработку', kind: 'secondary', comment: 'Укажите причину' },
  NOT_DONE: { title: 'Не выполнено', kind: 'secondary', comment: 'Укажите причину' },
  RELEASE: { title: 'Снять себя', kind: 'secondary', comment: null },
  FORWARD: { title: 'Передать в депо', kind: 'secondary', comment: 'Укажите причину' },
  DEFER: { title: 'Отложить до ТО', kind: 'secondary', comment: 'Укажите причину' },
  REJECT: { title: 'Отклонить', kind: 'secondary', comment: 'Укажите причину' },
  CANCEL: { title: 'Отменить', kind: 'secondary', comment: 'Укажите причину' },
  DETACH: { title: 'ТОР — отцепить вагон', kind: 'danger', comment: 'Укажите причину' },
}

const has = (user: CurrentUser, role: string) => user.roles.includes(role)

const isAdmin = (user: CurrentUser) => has(user, 'ADMIN')

/** Вагон заявки едет в текущем рейсе пользователя. */
function inMyTrip(user: CurrentUser, r: RepairRequest): boolean {
  return !!user.trip && r.wagonTrip === user.trip.id
}

/** Вагон заявки не в рейсе и стоит на станции депо пользователя. */
function atMyDepot(user: CurrentUser, r: RepairRequest): boolean {
  return !r.wagonTrip && !!r.wagonStation && r.wagonStation === user.depotStation
}

/** Мастер депо, которому подчиняется заявка: вагон на станции его депо. */
function isMyMaster(user: CurrentUser, r: RepairRequest): boolean {
  return has(user, 'MASTER') && atMyDepot(user, r)
}

/**
 * Сотрудник службы заявки, которому она видна (Ф-ЗАЯ-06): вагон в рейсе — в бригаде этого рейса; иначе — депо на
 * станции вагона. Такой сотрудник берёт заявку в работу и отклоняет её.
 */
export function canWork(user: CurrentUser, r: RepairRequest): boolean {
  if (isAdmin(user)) return true
  if (!user.services.includes(r.service)) return false
  // заявку, переданную в депо, бригада рейса уже не исполняет
  if (inMyTrip(user, r)) return !r.forwarded
  return atMyDepot(user, r)
}

/**
 * Кто принимает работу (Ф-ЗАЯ-03): вагон в рейсе — проводник этого вагона и начальник поезда рейса; вагон не в рейсе
 * — инструктор поездных бригад и эксплуатационщик депо на станции вагона.
 */
export function canAccept(user: CurrentUser, r: RepairRequest): boolean {
  if (isAdmin(user)) return true
  if (inMyTrip(user, r)) {
    const trip = user.trip!
    return trip.duty === 'LNP' || (trip.duty === 'CONDUCTOR' && trip.wagons.includes(r.wagon))
  }
  return (has(user, 'INSTRUCTOR') || has(user, 'OPERATOR')) && atMyDepot(user, r)
}

/** Действия, которые пользователь может выполнить над заявкой сейчас, в порядке показа кнопок. */
export function requestActions(user: CurrentUser, r: RepairRequest): RequestAction[] {
  const s = r.status
  if (FINAL_STATUSES.includes(s)) return []
  const admin = isAdmin(user)
  const executor = r.executor?.id === user.id
  const master = isMyMaster(user, r) || admin
  const work = canWork(user, r)
  const pem = has(user, 'PEM') && inMyTrip(user, r) && user.trip!.duty === 'PEM'
  const result = new Set<RequestAction>()

  // назначенную заявку берёт только назначенный исполнитель
  if (['NEW', 'DEFERRED'].includes(s) && (work || master)) result.add('TAKE')
  if (s === 'ASSIGNED' && (executor || admin)) result.add('TAKE')
  if (s === 'IN_PROGRESS' && (executor || admin)) {
    result.add('COMPLETE')
    result.add('NOT_DONE')
    result.add('RELEASE')
  }
  if (s === 'DONE' && canAccept(user, r)) {
    result.add('ACCEPT')
    result.add('RETURN')
  }
  if (['NEW', 'ASSIGNED', 'IN_PROGRESS'].includes(s) && ((pem && !r.forwarded) || admin)) result.add('FORWARD')
  if (['NEW', 'ASSIGNED', 'IN_PROGRESS'].includes(s) && master) result.add('DEFER')
  if (['NEW', 'ASSIGNED'].includes(s) && (work || master)) result.add('REJECT')
  if (master) result.add('CANCEL')
  if (['NEW', 'ASSIGNED', 'IN_PROGRESS'].includes(s) && master && !r.tor) result.add('DETACH')

  return (Object.keys(ACTIONS) as RequestAction[]).filter((a) => result.has(a))
}

/** Назначить исполнителя может мастер депо станции вагона, пока заявка не закончена (Ф-ЗАЯ-02). */
export function canAssign(user: CurrentUser, r: RepairRequest): boolean {
  return !FINAL_STATUSES.includes(r.status) && (isMyMaster(user, r) || isAdmin(user))
}

/**
 * Автор исправляет заявку в течение 10 минут после подачи, пока не сменились исполнитель и статус (Ф-ЗАЯ-07).
 * Проверка только для показа кнопки: окончательно её делает сервер.
 */
export function canEdit(user: CurrentUser, r: RepairRequest, now = Date.now()): boolean {
  if (r.author.id !== user.id || r.status !== 'NEW' || r.executor || r.forwarded) return false
  return now - new Date(r.created).getTime() <= EDIT_MINUTES * 60_000
}

/** Почему исправить нельзя — подсказка на кнопке; пусто — можно. Текст на русском, переводится при показе. */
export function editBlockReason(user: CurrentUser, r: RepairRequest): string {
  if (r.author.id !== user.id) return 'Исправить может только автор заявки'
  if (canEdit(user, r)) return ''
  return 'Исправить можно в течение 10 минут после подачи, пока не сменились исполнитель и статус'
}

/** Мастер депо может выбрать исполнителя при подаче заявки (Ф-ЗАЯ-02). */
export function canAssignOnCreate(user: CurrentUser): boolean {
  return has(user, 'MASTER') || isAdmin(user)
}
