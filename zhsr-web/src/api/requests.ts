import type { TableSort } from '@/shared/components/table/types'
import { http } from './http'
import type { TripRef } from './wagons'

/**
 * API заявок на ремонт (Ф-ЗАЯ-01 … Ф-ЗАЯ-07): сервер zhsr-app, RequestController. Подача, исправление и примечание —
 * multipart: черновик частью draft (JSON) или текст полем text, фото — частями photos.
 */

/** Сотрудник в данных: id — для запросов, name — фамилия и инициалы для показа. */
export interface UserRef {
  id: number
  name: string
}

/** Вагон в данных. */
export interface WagonRef {
  id: number
  number: string
  /** тип вагона — id справочника WagonType */
  wagonType: number
}

/**
 * Действие над заявкой (Ф-ЗАЯ-03, Ф-ЗАЯ-07): взять в работу, выполнено, снять себя, принять работу, вернуть на
 * доработку, отклонить, отложить до ТО, отменить.
 */
export type RequestAction = 'TAKE' | 'COMPLETE' | 'RELEASE' | 'ACCEPT' | 'RETURN' | 'REJECT' | 'DEFER' | 'CANCEL'

/** Заявка на ремонт. Сотрудник и рейс — объектом: id и имя для показа. */
export interface RepairRequest {
  id: number
  /** номер: код компании, год, порядковый номер в году — «KZR-26-00042» */
  number: string
  wagon: WagonRef
  /** вид расположения вагона сейчас — id справочника LocationKind; null — расположение не задано */
  locationType: number | null
  /** где вагон сейчас: станция, депо или парк отстоя — id справочника по виду расположения; null — вагон в рейсе */
  place: number | null
  /** рейс, в составе которого вагон едет сейчас; null — вагон не в рейсе */
  wagonTrip: TripRef | null
  /** станция расположения вагона — id справочника Station; null — вагон в рейсе */
  wagonStation: number | null
  /** рейс заявки */
  trip: TripRef | null
  /**
   * служба-исполнитель — id справочника Service (константы ServiceType); null — только у заявки из очереди телефона,
   * если её неисправности нет в справочнике
   */
  service: number | null
  /** неисправность — id справочника FaultType */
  faultType: number
  /** критичность — id справочника Severity (константы Severity) */
  severityType: number
  /** автор заявки */
  createdByUsr: UserRef
  /** время создания заявки пользователем, ISO */
  createdDt: string
  /** время поступления заявки на сервер, ISO; заявка, поданная без связи, поступает позже создания */
  receivedDt: string
  description: string
  /** статус — id справочника RequestStatus (константы RequestStatus) */
  requestStatusType: number
  /** текущий исполнитель; null — не назначен */
  executorUsr: UserRef | null
  /** сколько фото приложено к заявке и примечаниям */
  photoCount: number
}

/** Фото заявки или примечания. url — адрес файла; null — файла пока нет (показывается заглушка). */
export interface RequestPhoto {
  id: string
  url: string | null
}

/** Фото заявки на сервере. */
export interface StoredPhoto extends RequestPhoto {
  /** когда приложено, ISO */
  createdDt: string
  /** когда поступило на сервер, ISO */
  receivedDt: string
  createdByUsr: UserRef
}

/** Запись журнала статусов. */
export interface RequestStatusRecord {
  /** начало записи, ISO */
  dbeg: string
  /** статус — id справочника RequestStatus */
  requestStatusType: number
  /** кто внёс запись; null — Система */
  createdByUsr: UserRef | null
}

/** Запись журнала исполнителей. */
export interface RequestExecutorRecord {
  /** начало записи, ISO */
  dbeg: string
  /** исполнитель; null — назначение снято */
  executorUsr: UserRef | null
  /** кто внёс запись; null — Система */
  createdByUsr: UserRef | null
}

/** Примечание к заявке. */
export interface RequestNote {
  /** когда написано, ISO */
  createdDt: string
  /** когда поступило на сервер, ISO */
  receivedDt: string
  createdByUsr: UserRef
  note: string
}

/** Заявка с плоскими списками журналов, примечаний и фото: историю из них строит shared/requests/historyTree.ts. */
export interface RequestDetail extends RepairRequest {
  statuses: RequestStatusRecord[]
  executors: RequestExecutorRecord[]
  notes: RequestNote[]
  photos: StoredPhoto[]
}

/**
 * Список заявок (Ф-ЗАЯ-06): mine — «созданные мной»; todo — «мне на исполнение»: назначены мне, в работе у меня,
 * ждут моего приёма; all — «все»: видные мне по службе, ждут моего действия или поданы мной.
 */
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
  /** когда пользователь подал или исправил заявку, ISO: задаётся при отправке из очереди; нет — время сервера */
  clientDt?: string
  /** guid записи с телефона: сервер принимает её один раз, повторная отправка после обрыва связи дубля не создаёт */
  clientGuid?: string
}

/** Сколько заявок по фильтру в каждом статусе и каждой критичности — числа у кнопок фильтров. */
export interface RequestCounts {
  requestStatusType: Record<number, number>
  severityType: Record<number, number>
}

/** Фото в ответе сервера: файл — GET /requests/photos/{id}. */
type ServerPhoto = Omit<StoredPhoto, 'id' | 'url'> & { id: number }

type ServerDetail = Omit<RequestDetail, 'photos'> & { photos: ServerPhoto[] }

function detailOf(d: ServerDetail): RequestDetail {
  return {
    ...d,
    photos: d.photos.map((p) => ({ ...p, id: String(p.id), url: http.getUri({ url: `/requests/photos/${p.id}` }) })),
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

/**
 * Выполнить действие над заявкой (Ф-ЗАЯ-03, Ф-ЗАЯ-07); comment пишется примечанием к заявке. clientDt — когда
 * пользователь выполнил действие, ISO, и clientGuid — guid записи: при отправке из очереди. Если после clientDt у
 * заявки сменились статус или исполнитель, сервер отказывает: действие несвоевременно.
 */
export async function runRequestAction(
  id: number,
  action: RequestAction,
  comment = '',
  clientDt: string | null = null,
  clientGuid: string | null = null,
): Promise<RequestDetail> {
  const body = { comment, clientDt, clientGuid }
  return detailOf((await http.post<ServerDetail>(`/requests/${id}/actions/${action}`, body)).data)
}

/**
 * Назначить исполнителя или снять назначение (executor = null) — Ф-ЗАЯ-02. clientDt и clientGuid — как у
 * runRequestAction.
 */
export async function assignRequest(
  id: number,
  executor: number | null,
  clientDt: string | null = null,
  clientGuid: string | null = null,
): Promise<RequestDetail> {
  return detailOf((await http.put<ServerDetail>(`/requests/${id}/executor`, { executor, clientDt, clientGuid })).data)
}

/**
 * Примечание к заявке: текст и фото (Ф-ЗАЯ-05). clientDt — когда написано, ISO: при отправке из очереди; clientGuid —
 * guid записи с телефона.
 */
export async function addRequestNote(
  id: number,
  text: string,
  photos: Blob[],
  clientDt?: string,
  clientGuid?: string,
): Promise<RequestDetail> {
  const form = new FormData()
  form.append('text', text)
  if (clientDt) form.append('clientDt', clientDt)
  if (clientGuid) form.append('clientGuid', clientGuid)
  photos.forEach((p) => form.append('photos', p))
  return detailOf((await http.post<ServerDetail>(`/requests/${id}/notes`, form)).data)
}

/** Одно фото к заявке с телефона: каждое фото — своя отправка. clientDt — время заявки или примечания, ISO. */
export async function addRequestPhoto(id: number, photo: Blob, clientDt: string, clientGuid: string): Promise<void> {
  const form = new FormData()
  form.append('photo', photo)
  form.append('clientDt', clientDt)
  form.append('clientGuid', clientGuid)
  await http.post(`/requests/${id}/photos`, form)
}

/** Сотрудники службы — кому можно назначить заявку. */
export async function fetchServiceStaff(service: number): Promise<UserRef[]> {
  return (await http.get<UserRef[]>(`/services/${service}/staff`)).data
}
