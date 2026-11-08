import type { RepairRequest, RequestAction } from '@/api/requests'
import type { CurrentUser } from '@/api/session'
import { CrewDuty, RequestStatus, RoleType } from '@/shared/constants/systemDicts'

/**
 * Права пользователя на заявку на ремонт (Ф-ЗАЯ-02, Ф-ЗАЯ-03, Ф-ЗАЯ-07): какие действия показать. Решает клиент по
 * данным пользователя (роли, службы, депо, текущий рейс и должность в нём) и полям заявки (автор, исполнитель, где
 * вагон). Сервер проверяет права сам; здесь — только что показать.
 */

/** Статусы, в которых заявка закончена: действий по ней нет. */
export const FINAL_STATUSES: number[] = [RequestStatus.CLOSED, RequestStatus.REJECTED, RequestStatus.CANCELLED]

/** Открытые статусы — для отбора «открытые»; выполненная заявка ещё ждёт приёмки, поэтому тоже открыта. */
export const OPEN_STATUSES: number[] = [
  RequestStatus.NEW,
  RequestStatus.ASSIGNED,
  RequestStatus.IN_PROGRESS,
  RequestStatus.DONE,
  RequestStatus.DEFERRED,
]

/** Сколько минут после подачи автор может исправить заявку (Ф-ЗАЯ-07). */
const EDIT_MINUTES = 10

export interface ActionDef {
  /** текст кнопки на русском; переводится при показе */
  title: string
  /** вид кнопки: основная, обычная или опасная (красная) */
  kind: 'primary' | 'secondary' | 'danger'
  /** подсказка поля комментария на русском; null — комментарий не нужен */
  comment: string | null
  /** к действию можно приложить фото */
  photos?: boolean
  /** граф статусов: из каких статусов действие выполняется — константы RequestStatus; как на сервере (RequestAction) */
  from: number[]
}

const S = RequestStatus

/** Действия над заявкой в порядке показа кнопок и граф статусов (from). */
export const ACTIONS: Record<RequestAction, ActionDef> = {
  TAKE: { title: 'Взять в работу', kind: 'primary', comment: null, from: [S.NEW, S.ASSIGNED, S.DEFERRED] },
  COMPLETE: {
    title: 'Выполнено',
    kind: 'primary',
    comment: 'Что сделано — попадёт в журнал ВУ-8',
    photos: true,
    from: [S.IN_PROGRESS],
  },
  ACCEPT: { title: 'Принять работу', kind: 'primary', comment: null, from: [S.DONE] },
  RETURN: {
    title: 'Вернуть на доработку',
    kind: 'secondary',
    comment: 'Укажите причину',
    photos: true,
    from: [S.DONE],
  },
  RELEASE: { title: 'Снять себя', kind: 'secondary', comment: null, from: [S.IN_PROGRESS] },
  DEFER: {
    title: 'Отложить до ТО',
    kind: 'secondary',
    comment: 'Укажите причину',
    from: [S.NEW, S.ASSIGNED, S.IN_PROGRESS],
  },
  REJECT: {
    title: 'Отклонить',
    kind: 'secondary',
    comment: 'Укажите причину',
    photos: true,
    from: [S.NEW, S.ASSIGNED],
  },
  CANCEL: {
    title: 'Отменить',
    kind: 'secondary',
    comment: 'Укажите причину',
    photos: true,
    from: [S.NEW, S.ASSIGNED, S.DEFERRED],
  },
}

const has = (user: CurrentUser, role: number) => user.roles.includes(role)

const isAdmin = (user: CurrentUser) => has(user, RoleType.ADMIN)

/** Вагон заявки едет в текущем рейсе пользователя. */
function inMyTrip(user: CurrentUser, r: RepairRequest): boolean {
  return !!user.trip && r.wagonTrip?.id === user.trip.id
}

/** Вагон заявки не в рейсе и стоит на станции депо пользователя. */
function atMyDepot(user: CurrentUser, r: RepairRequest): boolean {
  return !r.wagonTrip && !!r.wagonStation && r.wagonStation === user.depotStation
}

/** Мастер депо, которому подчиняется заявка: вагон на станции его депо. */
function isMyMaster(user: CurrentUser, r: RepairRequest): boolean {
  return has(user, RoleType.MASTER) && atMyDepot(user, r)
}

/**
 * Сотрудник службы заявки, которому она видна (Ф-ЗАЯ-06): вагон в рейсе — в бригаде этого рейса; иначе — депо на
 * станции вагона. Такой сотрудник берёт заявку в работу и отклоняет её.
 */
export function canWork(user: CurrentUser, r: RepairRequest): boolean {
  if (isAdmin(user)) return true
  if (r.service === null || !user.services.includes(r.service)) return false
  return inMyTrip(user, r) || atMyDepot(user, r)
}

/**
 * Кто принимает работу (Ф-ЗАЯ-03): вагон в рейсе — проводник этого вагона и начальник поезда рейса; вагон не в рейсе
 * — инструктор поездных бригад и эксплуатационщик депо на станции вагона.
 */
export function canAccept(user: CurrentUser, r: RepairRequest): boolean {
  if (isAdmin(user)) return true
  if (inMyTrip(user, r)) {
    const trip = user.trip!
    return (
      trip.crewDutyType === CrewDuty.LNP ||
      (trip.crewDutyType === CrewDuty.CONDUCTOR && trip.wagons.includes(r.wagon.number))
    )
  }
  return (has(user, RoleType.INSTRUCTOR) || has(user, RoleType.OPERATOR)) && atMyDepot(user, r)
}

/**
 * Заявка ждёт действия лично пользователя — список «мне на исполнение» (Ф-ЗАЯ-06), как на сервере: назначена ему или
 * в работе у него; выполнена и он принимает работу.
 */
export function waitsForMe(user: CurrentUser, r: RepairRequest): boolean {
  switch (r.requestStatusType) {
    case RequestStatus.ASSIGNED:
    case RequestStatus.IN_PROGRESS:
      return r.executorUsr?.id === user.id
    case RequestStatus.DONE:
      return canAccept(user, r)
    default:
      return false
  }
}

/**
 * Заявка видна пользователю — список «все» (Ф-ЗАЯ-06), как на сервере: видна ему для исполнения по службе, ждёт его
 * действия или подана им; администратору — все.
 */
export function seenByMe(user: CurrentUser, r: RepairRequest): boolean {
  return canWork(user, r) || waitsForMe(user, r) || r.createdByUsr.id === user.id
}

/**
 * Нет прав на действие — кнопку не показывают: как на сервере (RequestRights.blockReason), без статуса. Текст на
 * русском; пусто — права есть.
 */
export function actionRightsReason(user: CurrentUser, r: RepairRequest, action: RequestAction): string {
  const admin = isAdmin(user)
  const executor = r.executorUsr?.id === user.id || admin
  const master = isMyMaster(user, r) || admin
  const work = canWork(user, r) || master
  switch (action) {
    // назначенную заявку берёт только назначенный исполнитель
    case 'TAKE':
      if (r.requestStatusType === S.ASSIGNED) return executor ? '' : 'Берёт в работу только назначенный исполнитель'
      return work ? '' : 'Берёт в работу сотрудник службы заявки, которому она видна, или мастер депо'
    case 'COMPLETE':
    case 'RELEASE':
      return executor ? '' : 'Только исполнитель заявки'
    case 'ACCEPT':
    case 'RETURN':
      return canAccept(user, r)
        ? ''
        : 'Принимает работу: вагон в рейсе — ЛНП рейса или проводник вагона, иначе — инструктор поездных бригад или эксплуатационщик депо станции вагона'
    // выполнить невозможно — откладывает мастер депо или исполнитель заявки в работе
    case 'DEFER':
      return master || (executor && r.requestStatusType === S.IN_PROGRESS)
        ? ''
        : 'Откладывает мастер депо станции вагона или исполнитель заявки в работе'
    case 'REJECT':
      return work ? '' : 'Отклоняет сотрудник службы заявки или мастер депо'
    case 'CANCEL':
      return r.createdByUsr.id === user.id || admin ? '' : 'Отменяет только автор заявки'
  }
}

/** Действие недоступно в текущем статусе заявки (граф ACTIONS, from) — кнопка неактивна с этой причиной; пусто — доступно. */
export function actionStatusReason(r: RepairRequest, action: RequestAction): string {
  return ACTIONS[action].from.includes(r.requestStatusType) ? '' : 'Недоступно в статусе «{status}»'
}

/**
 * Действия над заявкой, на которые у пользователя есть права, в порядке показа кнопок; reason — почему действие
 * недоступно в текущем статусе (пусто — доступно). Заявка в конечном статусе — пусто: кнопок нет.
 */
export function requestActions(user: CurrentUser, r: RepairRequest): { action: RequestAction; reason: string }[] {
  if (FINAL_STATUSES.includes(r.requestStatusType)) return []
  return (Object.keys(ACTIONS) as RequestAction[])
    .filter((action) => !actionRightsReason(user, r, action))
    .map((action) => ({ action, reason: actionStatusReason(r, action) }))
}

/** Назначить исполнителя может мастер депо станции вагона, пока заявка не закончена (Ф-ЗАЯ-02). */
export function canAssign(user: CurrentUser, r: RepairRequest): boolean {
  return !FINAL_STATUSES.includes(r.requestStatusType) && (isMyMaster(user, r) || isAdmin(user))
}

/**
 * Автор исправляет заявку в течение 10 минут после подачи, пока не сменились исполнитель и статус (Ф-ЗАЯ-07).
 * Проверка только для показа кнопки: окончательно её делает сервер.
 */
export function canEdit(user: CurrentUser, r: RepairRequest, now = Date.now()): boolean {
  if (r.createdByUsr.id !== user.id || r.requestStatusType !== RequestStatus.NEW || r.executorUsr) return false
  return now - new Date(r.createdDt).getTime() <= EDIT_MINUTES * 60_000
}

/** Почему исправить нельзя — подсказка на кнопке; пусто — можно. Текст на русском, переводится при показе. */
export function editBlockReason(user: CurrentUser, r: RepairRequest): string {
  if (r.createdByUsr.id !== user.id) return 'Исправить может только автор заявки'
  if (canEdit(user, r)) return ''
  return 'Исправить можно в течение 10 минут после подачи, пока не сменились исполнитель и статус'
}

/** Мастер депо может выбрать исполнителя при подаче заявки (Ф-ЗАЯ-02). */
export function canAssignOnCreate(user: CurrentUser): boolean {
  return has(user, RoleType.MASTER) || isAdmin(user)
}
