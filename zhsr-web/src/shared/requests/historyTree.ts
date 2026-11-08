import type { RequestAction, RequestDetail, RequestPhoto, UserRef } from '@/api/requests'
import { RequestStatus } from '@/shared/constants/systemDicts'
import type { OutboxItem } from '@/shared/offline/outbox'

/**
 * Дерево истории заявки. Сервер отдаёт плоские списки: журналы, примечания, фото. Здесь они и записи очереди собираются
 * в узлы, а вёрстка получает готовые записи и ничего не группирует.
 *
 * Правило одно: всё, что один сотрудник внёс в одну секунду, — один узел: действие, текст, фото. Узел — одна запись
 * истории. Фото момента подачи — фото самой заявки, они показываются при её описании.
 */

/** Запись истории: подача, смена статуса, назначение исполнителя или примечание. */
export interface RequestEvent {
  /** время, ISO */
  at: string
  /** кто внёс запись; null — Система */
  by: UserRef | null
  kind: 'CREATED' | 'STATUS' | 'ASSIGN' | 'NOTE'
  /** STATUS: действие, которым сменился статус */
  action?: RequestAction
  /** ASSIGN: новый исполнитель; null — назначение снято */
  executor?: UserRef | null
  /** комментарий к действию или текст примечания */
  text?: string
  photos: RequestPhoto[]
  /** примечание и фото: время поступления на сервер, ISO */
  receivedAt?: string
}

/** Узел истории: запись сервера и ожидающие записи с тем же временем, или только ожидающие. */
export interface HistoryNode {
  key: string
  event: RequestEvent | null
  queued: OutboxItem[]
}

export interface HistoryTree {
  /** фото заявки на сервере */
  photos: RequestPhoto[]
  /** ожидающие фото момента подачи */
  own: OutboxItem[]
  nodes: HistoryNode[]
}

const secondOf = (iso: string) => Math.floor(Date.parse(iso) / 1000)
const momentKey = (by: UserRef | null, at: string) => `${by?.id ?? ''}@${secondOf(at)}`

/** Всё, что один сотрудник внёс в один момент. */
interface Moment {
  at: string
  by: UserRef | null
  status?: number
  /** задан — запись журнала исполнителей есть; null внутри — назначение снято */
  executor?: { usr: UserRef | null }
  note?: string
  photos: RequestPhoto[]
  receivedAt?: string
}

/** Моменты журналов: записи статуса и исполнителя с одним временем — один момент. */
function journalMoments(d: RequestDetail): Moment[] {
  const moments = new Map<number, Moment>()
  const momentOf = (at: string, by: UserRef | null) => {
    const ms = Date.parse(at)
    const moment = moments.get(ms) ?? { at, by, photos: [] }
    moments.set(ms, moment)
    return moment
  }
  for (const r of d.statuses) momentOf(r.dbeg, r.createdByUsr).status = r.requestStatusType
  for (const r of d.executors) momentOf(r.dbeg, r.createdByUsr).executor = { usr: r.executorUsr }
  return [...moments.values()]
}

/** Моменты по времени и фото подачи: журналы, затем примечания и фото — в момент того же сотрудника в ту же секунду. */
function serverMoments(d: RequestDetail): { root: Moment; moments: Moment[] } {
  const root: Moment = { at: d.createdDt, by: d.createdByUsr, photos: [] }
  const byKey = new Map([[momentKey(root.by, root.at), root]])
  const moments: Moment[] = []
  const add = (moment: Moment) => {
    moments.push(moment)
    if (!byKey.has(momentKey(moment.by, moment.at))) byKey.set(momentKey(moment.by, moment.at), moment)
    return moment
  }

  for (const moment of journalMoments(d)) {
    // запись статуса при подаче: событие подачи уже есть; исполнитель при подаче — событие назначения
    if (!moment.executor && Date.parse(moment.at) === Date.parse(root.at)) continue
    add(moment)
  }
  const byTime = (a: { createdDt: string }, b: { createdDt: string }) =>
    Date.parse(a.createdDt) - Date.parse(b.createdDt)
  for (const n of [...d.notes].sort(byTime)) {
    const found = byKey.get(momentKey(n.createdByUsr, n.createdDt))
    const free = found && found !== root && found.note === undefined
    const moment = free ? found : add({ at: n.createdDt, by: n.createdByUsr, photos: [] })
    moment.note = n.note
    moment.receivedAt = n.receivedDt
  }
  for (const p of [...d.photos].sort(byTime)) {
    const key = momentKey(p.createdByUsr, p.createdDt)
    const moment = byKey.get(key) ?? add({ at: p.createdDt, by: p.createdByUsr, photos: [] })
    moment.photos.push(p)
    moment.receivedAt ??= p.receivedDt
  }
  moments.sort((a, b) => Date.parse(a.at) - Date.parse(b.at))
  return { root, moments }
}

/** Каким действием сменился статус; null — это только назначение исполнителя. */
function actionOf(prev: number, status: number): RequestAction | null {
  if (status === prev) return null
  switch (status) {
    case RequestStatus.IN_PROGRESS:
      return prev === RequestStatus.DONE ? 'RETURN' : 'TAKE'
    case RequestStatus.DONE:
      return 'COMPLETE'
    case RequestStatus.CLOSED:
      return 'ACCEPT'
    case RequestStatus.NEW:
      return prev === RequestStatus.IN_PROGRESS ? 'RELEASE' : null
    case RequestStatus.ASSIGNED:
      return null
    case RequestStatus.REJECTED:
      return 'REJECT'
    case RequestStatus.DEFERRED:
      return 'DEFER'
    default:
      return 'CANCEL'
  }
}

/** Записи истории из моментов: первая — подача. Вид смены статуса зависит от прежнего, поэтому проход по времени. */
function serverEvents(d: RequestDetail, moments: Moment[]): RequestEvent[] {
  const events: RequestEvent[] = [{ at: d.createdDt, by: d.createdByUsr, kind: 'CREATED', photos: [] }]
  let status: number = RequestStatus.NEW
  let executor: UserRef | null = null
  for (const m of moments) {
    const common = {
      at: m.at,
      by: m.by,
      photos: m.photos,
      ...(m.note === undefined ? {} : { text: m.note }),
      ...(m.receivedAt === undefined ? {} : { receivedAt: m.receivedAt }),
    }
    if (m.status === undefined && !m.executor) {
      events.push({ ...common, kind: 'NOTE' })
      continue
    }
    const nextStatus = m.status ?? status
    const nextExecutor: UserRef | null = m.executor ? m.executor.usr : executor
    const action = actionOf(status, nextStatus)
    events.push(action ? { ...common, kind: 'STATUS', action } : { ...common, kind: 'ASSIGN', executor: nextExecutor })
    status = nextStatus
    executor = nextExecutor
  }
  return events
}

/**
 * Дерево. detail = null — истории нет (заявка из очереди или без связи), узлы только из очереди. createdDt — время
 * заявки: ожидающие фото этой секунды — фото заявки.
 */
export function historyTree(detail: RequestDetail | null, queued: OutboxItem[], createdDt: string | null): HistoryTree {
  const server = detail ? serverMoments(detail) : null
  const events = detail && server ? serverEvents(detail, server.moments) : []
  const nodes: HistoryNode[] = events.map((event, i) => ({ key: `event-${i}`, event, queued: [] }))
  const own: OutboxItem[] = []
  for (const item of queued) {
    const second = secondOf(item.created)
    if (item.kind === 'photo' && createdDt !== null && second === secondOf(createdDt)) {
      own.push(item)
      continue
    }
    // фото присоединяется и к записи сервера; текст и правка — всегда своя запись, сервер их ещё не видел
    const node = nodes.find((n) =>
      n.event
        ? item.kind === 'photo' && n.event.kind !== 'CREATED' && secondOf(n.event.at) === second
        : secondOf(n.queued[0]!.created) === second,
    )
    if (node) node.queued.push(item)
    else nodes.push({ key: item.id, event: null, queued: [item] })
  }
  return { photos: server?.root.photos ?? [], own, nodes }
}
