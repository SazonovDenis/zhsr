import type { TableSort } from '@/shared/components/table/types'
import { http } from './http'
import type { TripRef } from './wagons'

/**
 * API заявок на ремонт (Ф-ЗАЯ-01 … Ф-ЗАЯ-07): сервер zhsr-app, RequestController. Подача, исправление и пояснение —
 * multipart: черновик частью draft (JSON) или текст полем text, фото — частями photos.
 */

/** Сотрудник в данных: id — для запросов, name — фамилия и инициалы для показа. */
export interface UserRef {
  id: number
  name: string
}

/**
 * Действие над заявкой (Ф-ЗАЯ-03, Ф-ЗАЯ-07): взять в работу, выполнено, снять себя, принять работу, вернуть на
 * доработку, не выполнено, отклонить, отложить до ТО, отменить, передать в депо, ТОР — отцепить вагон.
 */
export type RequestAction =
  | 'TAKE'
  | 'COMPLETE'
  | 'RELEASE'
  | 'ACCEPT'
  | 'RETURN'
  | 'NOT_DONE'
  | 'REJECT'
  | 'DEFER'
  | 'CANCEL'
  | 'FORWARD'
  | 'DETACH'

/** Заявка на ремонт. Сотрудник и рейс — объектом: id и имя для показа. */
export interface RepairRequest {
  id: number
  /** номер: код компании, год, порядковый номер в году — «KZR-26-00042» */
  number: string
  /** вагон — id вагона */
  wagon: number
  /** номер вагона */
  wagonNumber: string
  /** тип вагона — id справочника WagonType */
  wagonType: number
  /** вид расположения вагона сейчас — id справочника LocationKind; null — расположение не задано */
  locationType: number | null
  /** где вагон сейчас: станция, депо или парк отстоя — id справочника по виду расположения; null — вагон в рейсе */
  place: number | null
  /** рейс, в составе которого вагон едет сейчас, — id рейса; null — вагон не в рейсе */
  wagonTrip: number | null
  /** рейс вагона для показа: номер поезда и маршрут; null — вагон не в рейсе */
  wagonTripName: string | null
  /** станция расположения вагона — id справочника Station; null — вагон в рейсе */
  wagonStation: number | null
  /** рейс заявки */
  trip: TripRef | null
  /** служба-исполнитель — id справочника Service (константы ServiceType) */
  service: number
  /** неисправность — id справочника FaultType */
  faultType: number
  /** критичность — id справочника Severity (константы Severity) */
  severityType: number
  /** автор заявки */
  createdByUsr: UserRef
  /** время подачи, ISO */
  createdDt: string
  description: string
  /** статус — id справочника RequestStatus (константы RequestStatus) */
  requestStatusType: number
  /** текущий исполнитель; null — не назначен */
  executorUsr: UserRef | null
  /** вагон отцеплен в ТОР по этой заявке; сервер пока не хранит — всегда false */
  tor: boolean
  /** ПЭМ передал заявку в депо: бригада рейса её уже не исполняет; сервер пока не хранит — всегда false */
  forwarded: boolean
  /** сколько фото приложено к заявке и пояснениям */
  photoCount: number
}

/** Фото заявки или пояснения. url — адрес файла; null — файла пока нет (показывается заглушка). */
export interface RequestPhoto {
  id: string
  url: string | null
}

/**
 * Запись истории заявки: подача, смена статуса (журнал repair_request_status), назначение исполнителя (журнал
 * repair_request_executor) или пояснение (repair_request_note).
 */
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
  /** комментарий к действию или текст пояснения */
  text?: string
  /** фото пояснения */
  photos?: RequestPhoto[]
}

/** Заявка с историей и фото — для карточки. */
export interface RequestDetail extends RepairRequest {
  /** история по времени, от старых к новым */
  events: RequestEvent[]
  /** фото, приложенные при подаче */
  photos: RequestPhoto[]
}

/** Список заявок (Ф-ЗАЯ-06): «мои» — поданные пользователем; «на исполнение» — видные ему или назначенные лично. */
export type RequestScope = 'mine' | 'todo' | 'all'

export interface RequestFilter {
  scope: RequestScope
  /** true — только закрытые (в конечном статусе), false — только открытые, null — все */
  closed: boolean | null
  /** поиск по номеру заявки, номеру вагона и неисправности */
  query: string
  /** статусы — id справочника RequestStatus */
  statuses: number[]
  /** критичность — id справочника Severity */
  severities: number[]
  /** службы — id справочника Service */
  services: number[]
  /** рейс — id рейса */
  trip: number | null
  /** период по времени подачи: ISO-даты «2026-10-30» включительно; null — без ограничения */
  from: string | null
  to: string | null
}

/** Пустой фильтр: все заявки списка scope. */
export function requestFilter(scope: RequestScope, patch: Partial<RequestFilter> = {}): RequestFilter {
  return {
    scope,
    closed: null,
    query: '',
    statuses: [],
    severities: [],
    services: [],
    trip: null,
    from: null,
    to: null,
    ...patch,
  }
}

/** Новая или исправленная заявка; photos — файлы. */
export interface RequestDraft {
  /** вагон — id вагона */
  wagon: number
  /** рейс — id рейса; null — без рейса */
  trip: number | null
  /** неисправность — id справочника FaultType */
  faultType: number
  /** критичность — id справочника Severity */
  severityType: number
  description: string
  /** новые фото — файлы с телефона или компьютера */
  photos: Blob[]
  /** исполнитель — id сотрудника; задаёт только мастер депо при подаче, null — не назначать */
  executorUsr: number | null
}

/** Сколько заявок по фильтру в каждом статусе и каждой критичности — числа у кнопок фильтров. */
export interface RequestCounts {
  requestStatusType: Record<number, number>
  severityType: Record<number, number>
}

/** Фото в ответе сервера: файл — GET /requests/photos/{id}. */
interface ServerPhoto {
  id: number
}

interface ServerEvent extends Omit<RequestEvent, 'photos'> {
  photos: ServerPhoto[]
}

interface ServerDetail extends RepairRequest {
  events: ServerEvent[]
  photos: ServerPhoto[]
}

/** Фото сервера — фото для показа: адрес файла на сервере. */
function photoOf(p: ServerPhoto): RequestPhoto {
  return { id: String(p.id), url: http.getUri({ url: `/requests/photos/${p.id}` }) }
}

function detailOf(d: ServerDetail): RequestDetail {
  return {
    ...d,
    photos: d.photos.map(photoOf),
    events: d.events.map((e) => ({ ...e, photos: e.photos.map(photoOf) })),
  }
}

/** Черновик и фото — тело multipart: черновик частью draft (JSON), фото — частями photos. */
function draftForm(draft: RequestDraft): FormData {
  const form = new FormData()
  const { photos, ...fields } = draft
  form.append('draft', new Blob([JSON.stringify(fields)], { type: 'application/json' }))
  photos.forEach((p) => form.append('photos', p))
  return form
}

/** Список заявок по фильтру. sort — поле и направление сортировки; null — сначала новые. */
export async function fetchRequests(filter: RequestFilter, sort: TableSort | null = null): Promise<RepairRequest[]> {
  const params = { ...filter, sort: sort?.field, desc: sort?.desc }
  return (await http.get<RepairRequest[]>('/requests', { params })).data
}

/** Числа у фильтров статуса и критичности: по фильтру без самих этих отборов. */
export async function fetchRequestCounts(filter: RequestFilter): Promise<RequestCounts> {
  return (await http.get<RequestCounts>('/requests/counts', { params: filter })).data
}

/** Заявка с историей и фото. */
export async function fetchRequest(id: number): Promise<RequestDetail> {
  return detailOf((await http.get<ServerDetail>(`/requests/${id}`)).data)
}

/** Подать заявку (Ф-ЗАЯ-01): Система присваивает номер, заявка — «новая» или «назначена», если задан исполнитель. */
export async function createRequest(draft: RequestDraft): Promise<RepairRequest> {
  return (await http.post<RepairRequest>('/requests', draftForm(draft))).data
}

/** Исправить заявку (Ф-ЗАЯ-07): автор — в течение 10 минут после подачи; в журналы исправление не попадает. */
export async function updateRequest(id: number, draft: RequestDraft): Promise<RepairRequest> {
  return (await http.put<RepairRequest>(`/requests/${id}`, draftForm(draft))).data
}

/** Выполнить действие над заявкой (Ф-ЗАЯ-03, Ф-ЗАЯ-07); comment пишется пояснением к заявке. */
export async function runRequestAction(id: number, action: RequestAction, comment = ''): Promise<RequestDetail> {
  return detailOf((await http.post<ServerDetail>(`/requests/${id}/actions/${action}`, { comment })).data)
}

/** Назначить исполнителя или снять назначение (executor = null) — Ф-ЗАЯ-02. */
export async function assignRequest(id: number, executor: number | null): Promise<RequestDetail> {
  return detailOf((await http.put<ServerDetail>(`/requests/${id}/executor`, { executor })).data)
}

/** Пояснение к заявке: текст и фото (Ф-ЗАЯ-05). */
export async function addRequestNote(id: number, text: string, photos: Blob[]): Promise<RequestDetail> {
  const form = new FormData()
  form.append('text', text)
  photos.forEach((p) => form.append('photos', p))
  return detailOf((await http.post<ServerDetail>(`/requests/${id}/notes`, form)).data)
}

/** Сотрудники службы — кому можно назначить заявку. */
export async function fetchServiceStaff(service: number): Promise<UserRef[]> {
  return (await http.get<UserRef[]>(`/services/${service}/staff`)).data
}
