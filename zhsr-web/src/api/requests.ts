import { canWork, FINAL_STATUSES, OPEN_STATUSES } from '@/shared/requests/rights'
import type { TableSort } from '@/shared/components/table/types'
import { fakeSort } from './fakeSort'
import { fakeTime, isoNow } from './fakeTime'
import { fakeNetwork } from './http'
import { fakeCurrentUser } from './session'
import type { TripRef } from './wagons'
import { fakeRequestWagon } from './wagons'
// import { http } from './http'

/**
 * API заявок на ремонт (Ф-ЗАЯ-01 … Ф-ЗАЯ-07). Сервер пока не готов: функции работают с фиктивными данными в памяти,
 * а над ними закомментирован будущий вызов REST.
 */

/** Сотрудник в данных: id — для запросов, name — фамилия и инициалы для показа. */
export interface UserRef {
  id: string
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

export interface RepairRequest {
  id: string
  /** номер: код компании, год, порядковый номер в году — «KZR-26-00042» */
  number: string
  /** номер вагона */
  wagon: string
  /** тип вагона — id справочника WagonType */
  wagonType: string
  /** где вагон сейчас — для показа */
  wagonPlace: string
  /** рейс, в составе которого вагон едет сейчас; null — вагон не в рейсе */
  wagonTrip: string | null
  /** станция расположения вагона — id справочника Station; null — вагон в рейсе */
  wagonStation: string | null
  /** рейс заявки */
  trip: TripRef | null
  /** служба-исполнитель — id справочника Service */
  service: string
  /** неисправность — id справочника FaultType */
  fault: string
  /** критичность — id справочника Severity */
  severity: string
  author: UserRef
  /** время подачи, ISO */
  created: string
  description: string
  /** статус — id справочника RequestStatus */
  status: string
  /** текущий исполнитель; null — не назначен */
  executor: UserRef | null
  /** вагон отцеплен в ТОР по этой заявке */
  tor: boolean
  /** ПЭМ передал заявку в депо: бригада рейса её уже не исполняет */
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
 * Запись истории заявки: подача, смена статуса (журнал repair_request_executor), назначение исполнителя или
 * пояснение (repair_request_note).
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
  statuses: string[]
  /** критичность — id справочника Severity */
  severities: string[]
  /** службы — id справочника Service */
  services: string[]
  /** рейс — id рейса */
  trip: string | null
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

/** Новая или исправленная заявка. */
export interface RequestDraft {
  /** номер вагона */
  wagon: string
  /** рейс — id рейса; null — без рейса */
  trip: string | null
  /** неисправность — id справочника FaultType */
  fault: string
  /** служба-исполнитель — id справочника Service */
  service: string
  /** критичность — id справочника Severity */
  severity: string
  description: string
  /** новые фото — файлы с телефона или компьютера */
  photos: Blob[]
  /** исполнитель — id сотрудника; задаёт только мастер депо при подаче, null — не назначать */
  executor: string | null
}

/** Сколько заявок по фильтру в каждом статусе и каждой критичности — числа у кнопок фильтров. */
export interface RequestCounts {
  statuses: Record<string, number>
  severities: Record<string, number>
}

/** Список заявок по фильтру. sort — поле и направление сортировки; null — сначала новые. */
export async function fetchRequests(filter: RequestFilter, sort: TableSort | null = null): Promise<RepairRequest[]> {
  // return (await http.get<RepairRequest[]>('/requests', { params: { ...filter, sort: sort?.field, desc: sort?.desc } })).data
  fakeNetwork()
  const rows = fakeFilter(filter).filter(
    (r) =>
      (filter.statuses.length === 0 || filter.statuses.includes(r.status)) &&
      (filter.severities.length === 0 || filter.severities.includes(r.severity)),
  )
  return Promise.resolve(fakeSort(rows, sort ?? { field: 'created', desc: true }).map(brief))
}

/** Числа у фильтров статуса и критичности: по фильтру без самих этих отборов. */
export async function fetchRequestCounts(filter: RequestFilter): Promise<RequestCounts> {
  // return (await http.get<RequestCounts>('/requests/counts', { params: filter })).data
  fakeNetwork()
  const counts: RequestCounts = { statuses: {}, severities: {} }
  for (const r of fakeFilter(filter)) {
    counts.statuses[r.status] = (counts.statuses[r.status] ?? 0) + 1
    counts.severities[r.severity] = (counts.severities[r.severity] ?? 0) + 1
  }
  return Promise.resolve(counts)
}

/** Заявка с историей и фото. */
export async function fetchRequest(id: string): Promise<RequestDetail> {
  // return (await http.get<RequestDetail>(`/requests/${id}`)).data
  fakeNetwork()
  return Promise.resolve(detail(fakeFind(id)))
}

/** Подать заявку (Ф-ЗАЯ-01): Система присваивает номер, заявка — «новая» или «назначена», если задан исполнитель. */
export async function createRequest(draft: RequestDraft): Promise<RepairRequest> {
  // const form = new FormData(); form.append('draft', JSON.stringify({ ...draft, photos: undefined }))
  // draft.photos.forEach((p) => form.append('photos', p))
  // return (await http.post<RepairRequest>('/requests', form)).data
  fakeNetwork()
  const me = fakeMe()
  const at = isoNow()
  const executor = draft.executor ? fakeStaffRef(draft.executor) : null
  const r: FakeRequest = {
    ...fakeWagonFields(draft.wagon),
    id: `R${fakeSeq}`,
    number: `KZR-26-${String(fakeSeq++).padStart(5, '0')}`,
    trip: fakeTripRef(draft.trip),
    service: draft.service,
    fault: draft.fault,
    severity: draft.severity,
    author: me,
    created: at,
    description: draft.description,
    status: executor ? 'ASSIGNED' : 'NEW',
    executor,
    tor: false,
    forwarded: false,
    photoCount: draft.photos.length,
    photos: draft.photos.map(fakePhoto),
    events: [{ at, by: me, kind: 'CREATED' }, ...(executor ? [{ at, by: me, kind: 'ASSIGN' as const, executor }] : [])],
  }
  FAKE_REQUESTS.unshift(r)
  return Promise.resolve(brief(r))
}

/** Исправить заявку (Ф-ЗАЯ-07): автор — в течение 10 минут после подачи; в журналы исправление не попадает. */
export async function updateRequest(id: string, draft: RequestDraft): Promise<RepairRequest> {
  // return (await http.put<RepairRequest>(`/requests/${id}`, form)).data
  fakeNetwork()
  const r = fakeFind(id)
  Object.assign(r, fakeWagonFields(draft.wagon), {
    trip: fakeTripRef(draft.trip),
    service: draft.service,
    fault: draft.fault,
    severity: draft.severity,
    description: draft.description,
  })
  r.photos.push(...draft.photos.map(fakePhoto))
  r.photoCount = fakePhotoCount(r)
  return Promise.resolve(brief(r))
}

/** Выполнить действие над заявкой (Ф-ЗАЯ-03, Ф-ЗАЯ-07); comment пишется пояснением к заявке. */
export async function runRequestAction(id: string, action: RequestAction, comment = ''): Promise<RequestDetail> {
  // return (await http.post<RequestDetail>(`/requests/${id}/actions/${action}`, { comment })).data
  fakeNetwork()
  const r = fakeFind(id)
  const me = fakeMe()
  const next: Record<RequestAction, string> = {
    TAKE: 'IN_PROGRESS',
    COMPLETE: 'DONE',
    RELEASE: 'NEW',
    ACCEPT: 'CLOSED',
    RETURN: 'IN_PROGRESS',
    NOT_DONE: 'REJECTED',
    REJECT: 'REJECTED',
    DEFER: 'DEFERRED',
    CANCEL: 'CANCELLED',
    FORWARD: 'NEW',
    DETACH: 'IN_PROGRESS',
  }
  r.status = next[action]
  if (action === 'TAKE' || action === 'DETACH') r.executor = me
  if (action === 'RELEASE' || action === 'FORWARD') r.executor = null
  if (action === 'FORWARD') r.forwarded = true
  if (action === 'DETACH') r.tor = true
  r.events.push({ at: isoNow(), by: me, kind: 'STATUS', action, ...(comment ? { text: comment } : {}) })
  return Promise.resolve(detail(r))
}

/** Назначить исполнителя или снять назначение (executor = null) — Ф-ЗАЯ-02. */
export async function assignRequest(id: string, executor: string | null): Promise<RequestDetail> {
  // return (await http.put<RequestDetail>(`/requests/${id}/executor`, { executor })).data
  fakeNetwork()
  const r = fakeFind(id)
  r.executor = executor ? fakeStaffRef(executor) : null
  if (r.status === 'NEW' && r.executor) r.status = 'ASSIGNED'
  if (r.status === 'ASSIGNED' && !r.executor) r.status = 'NEW'
  r.events.push({ at: isoNow(), by: fakeMe(), kind: 'ASSIGN', executor: r.executor })
  return Promise.resolve(detail(r))
}

/** Пояснение к заявке: текст и фото (Ф-ЗАЯ-05). */
export async function addRequestNote(id: string, text: string, photos: Blob[]): Promise<RequestDetail> {
  // return (await http.post<RequestDetail>(`/requests/${id}/notes`, form)).data
  fakeNetwork()
  const r = fakeFind(id)
  r.events.push({ at: isoNow(), by: fakeMe(), kind: 'NOTE', text, photos: photos.map(fakePhoto) })
  r.photoCount = fakePhotoCount(r)
  return Promise.resolve(detail(r))
}

/** Сотрудники службы — кому можно назначить заявку. */
export async function fetchServiceStaff(service: string): Promise<UserRef[]> {
  // return (await http.get<UserRef[]>(`/services/${service}/staff`)).data
  fakeNetwork()
  return Promise.resolve(FAKE_STAFF[service] ?? [])
}

// --- фиктивные данные: значения из макета ---

type FakeRequest = RequestDetail

let fakeSeq = 46

function fakeMe(): UserRef {
  const user = fakeCurrentUser()
  return { id: user.id, name: user.name }
}

function fakeFind(id: string): FakeRequest {
  const r = FAKE_REQUESTS.find((x) => x.id === id)
  if (!r) throw new Error('Заявка не найдена')
  return r
}

/** Заявки по списку, открытости, поиску, службе, рейсу и периоду. */
function fakeFilter(filter: RequestFilter): FakeRequest[] {
  const user = fakeCurrentUser()
  const q = filter.query.trim().toLowerCase()
  return FAKE_REQUESTS.filter((r) => {
    if (filter.scope === 'mine' && r.author.id !== user.id) return false
    if (filter.scope === 'todo' && r.executor?.id !== user.id && !canWork(user, r)) return false
    if (filter.closed === true && !FINAL_STATUSES.includes(r.status)) return false
    if (filter.closed === false && !OPEN_STATUSES.includes(r.status)) return false
    if (filter.services.length > 0 && !filter.services.includes(r.service)) return false
    if (filter.trip && r.trip?.id !== filter.trip) return false
    if (filter.from && r.created.slice(0, 10) < filter.from) return false
    if (filter.to && r.created.slice(0, 10) > filter.to) return false
    if (q && !`${r.number} ${r.wagon} ${FAKE_FAULT_NAMES[r.fault] ?? ''}`.toLowerCase().includes(q)) return false
    return true
  })
}

/** Заявка без истории — строка списка. */
function brief(r: FakeRequest): RepairRequest {
  const copy: Partial<FakeRequest> = { ...r }
  delete copy.events
  delete copy.photos
  return copy as RepairRequest
}

function detail(r: FakeRequest): RequestDetail {
  return { ...r, events: [...r.events], photos: [...r.photos] }
}

function fakeWagonFields(number: string) {
  const w = fakeRequestWagon(number)
  return {
    wagon: number,
    wagonType: w?.type ?? '',
    wagonPlace: w?.place ?? '',
    wagonTrip: w?.trip?.id ?? null,
    wagonStation: w?.station ?? null,
  }
}

function fakeTripRef(id: string | null): TripRef | null {
  return id ? { id, name: id === 'T2' ? '002 Алматы — Астана' : id } : null
}

function fakePhoto(blob: Blob, i: number): RequestPhoto {
  return { id: `P${Date.now()}-${i}`, url: URL.createObjectURL(blob) }
}

function fakePhotoCount(r: FakeRequest): number {
  return r.photos.length + r.events.reduce((n, e) => n + (e.photos?.length ?? 0), 0)
}

function fakeStaffRef(id: string): UserRef {
  return (
    Object.values(FAKE_STAFF)
      .flat()
      .find((u) => u.id === id) ?? { id, name: id }
  )
}

const FAKE_STAFF: Record<string, UserRef[]> = {
  TECHNICAL: [
    { id: 'u7', name: 'Касымов Д. Е.' },
    { id: 'u8', name: 'Нуртаев А. С.' },
    { id: 'u9', name: 'Жумабаев Е. К.' },
    { id: 'u4', name: 'Оспанова М. Т.' },
    { id: 'u2', name: 'Жумабаева Г. С.' },
  ],
  CLEANING: [
    { id: 'u31', name: 'Иванова О. П.' },
    { id: 'u32', name: 'Серикова Г. А.' },
  ],
  EQUIPMENT: [
    { id: 'u33', name: 'Тулегенов К. М.' },
    { id: 'u34', name: 'Ли В. В.' },
  ],
}

// названия неисправностей для поиска; справочник FaultType заготовке API недоступен
const FAKE_FAULT_NAMES: Record<string, string> = {
  'FAV-1': 'Не греет отопление',
  'FAV-2': 'Окно дует',
  'FAV-3': 'Не работает замок в туалете',
  'FAV-4': 'Течь воды в туалете из-под потолка',
  'FAV-5': 'Нет аварийного освещения',
  'FAV-6': 'Не работают биотуалеты',
  'CLN-1': 'Грязно в туалете',
  'B-3': 'Не закрывается окно',
  'MSI-1': 'Не хватает подушек',
  'L-1': 'Не работает кондиционер',
  'K-1': 'Не горит свет в купе',
}

const U = {
  abenova: { id: 'u16', name: 'Абенова Д. С.' },
  tuleuova: { id: 'u20', name: 'Тулеуова Д. А.' },
  akhmetov: { id: 'u3', name: 'Ахметов Б. К.' },
  kasymov: { id: 'u7', name: 'Касымов Д. Е.' },
  ospanova: { id: 'u4', name: 'Оспанова М. Т.' },
  seitov: { id: 'u35', name: 'Сейтов Н. А.' },
  zhumabaeva: { id: 'u2', name: 'Жумабаева Г. С.' },
}

const T2: TripRef = { id: 'T2', name: '002 Алматы — Астана' }

function seed(
  id: number,
  wagon: string,
  hoursAgo: number,
  fields: Partial<FakeRequest> & Pick<FakeRequest, 'fault' | 'severity' | 'author'>,
  events: (Omit<RequestEvent, 'at'> & { h: number })[] = [],
): FakeRequest {
  const created = fakeTime(-hoursAgo)
  const photos = Array.from({ length: fields.photoCount ?? 0 }, (_, i) => ({ id: `S${id}-${i}`, url: null }))
  return {
    ...fakeWagonFields(wagon),
    id: `R${id}`,
    number: `KZR-26-${String(id).padStart(5, '0')}`,
    trip: null,
    service: 'TECHNICAL',
    description: '',
    status: 'NEW',
    executor: null,
    tor: false,
    forwarded: false,
    photoCount: 0,
    created,
    photos,
    ...fields,
    events: [
      { at: created, by: fields.author, kind: 'CREATED' },
      ...events.map(({ h, ...e }) => ({ ...e, at: fakeTime(-h) })),
    ],
  }
}

const FAKE_REQUESTS: FakeRequest[] = [
  seed(45, '15021', 1, { fault: 'K-1', severity: 'MEDIUM', author: U.ospanova }),
  seed(42, '17043', 0.1, {
    fault: 'FAV-2',
    severity: 'MEDIUM',
    author: U.abenova,
    trip: T2,
    description: 'В 2-м купе дует из-под рамы окна',
  }),
  seed(
    41,
    '17043',
    3,
    {
      fault: 'FAV-1',
      severity: 'HIGH',
      author: U.abenova,
      trip: T2,
      status: 'IN_PROGRESS',
      executor: U.kasymov,
      photoCount: 1,
      description:
        'В 4-м и 5-м купе холодно, батареи чуть тёплые. Пассажиры жалуются с вечера, в купе около +14 °C, в коридоре теплее. Регулятор в служебном купе стоит на максимуме, котёл работает, но давление в системе отопления ниже обычного. Прошу проверить на ближайшей стоянке, пассажирам выдали дополнительные одеяла',
    },
    [{ h: 2.5, by: U.kasymov, kind: 'STATUS', action: 'TAKE' }],
  ),
  seed(
    39,
    '17043',
    11,
    { fault: 'FAV-3', severity: 'MEDIUM', author: U.abenova, trip: T2, status: 'DONE', executor: U.kasymov },
    [
      { h: 10.5, by: U.kasymov, kind: 'STATUS', action: 'TAKE' },
      { h: 4, by: U.kasymov, kind: 'STATUS', action: 'COMPLETE', text: 'Заменил личинку замка' },
    ],
  ),
  seed(
    36,
    '27109',
    14,
    {
      fault: 'FAV-4',
      severity: 'CRITICAL',
      author: U.tuleuova,
      trip: T2,
      forwarded: true,
      photoCount: 2,
      description: 'Капает на пол, подставили ведро',
    },
    [{ h: 13.5, by: U.kasymov, kind: 'STATUS', action: 'FORWARD', text: 'В пути не устранить, нужен ремонт в депо' }],
  ),
  seed(
    35,
    '16532',
    15,
    { fault: 'FAV-5', severity: 'HIGH', author: U.akhmetov, trip: T2, status: 'ASSIGNED', executor: U.kasymov },
    [{ h: 14.8, by: U.zhumabaeva, kind: 'ASSIGN', executor: U.kasymov }],
  ),
  seed(
    33,
    '16540',
    48,
    { fault: 'FAV-6', severity: 'HIGH', author: U.ospanova, status: 'DONE', executor: U.ospanova },
    [
      { h: 47, by: U.ospanova, kind: 'STATUS', action: 'TAKE' },
      { h: 30, by: U.ospanova, kind: 'STATUS', action: 'COMPLETE', text: 'Прочистка, замена клапана' },
    ],
  ),
  seed(31, '15030', 20, {
    fault: 'CLN-1',
    severity: 'LOW',
    author: U.akhmetov,
    trip: T2,
    service: 'CLEANING',
  }),
  seed(
    29,
    '27140',
    96,
    { fault: 'B-3', severity: 'MEDIUM', author: U.abenova, status: 'DEFERRED', executor: U.ospanova },
    [{ h: 94, by: U.ospanova, kind: 'STATUS', action: 'DEFER', text: 'Замена уплотнителя на ТО-3' }],
  ),
  seed(
    27,
    '17043',
    120,
    {
      fault: 'MSI-1',
      severity: 'MEDIUM',
      author: U.abenova,
      service: 'EQUIPMENT',
      status: 'CLOSED',
      executor: U.seitov,
    },
    [
      { h: 119.5, by: U.seitov, kind: 'STATUS', action: 'TAKE' },
      { h: 119, by: U.seitov, kind: 'STATUS', action: 'COMPLETE', text: 'Довезли 12 комплектов' },
      { h: 118.8, by: U.abenova, kind: 'STATUS', action: 'ACCEPT' },
    ],
  ),
  seed(
    24,
    '15021',
    190,
    { fault: 'L-1', severity: 'HIGH', author: U.ospanova, status: 'CLOSED', executor: U.ospanova },
    [
      { h: 189, by: U.ospanova, kind: 'STATUS', action: 'TAKE' },
      { h: 185, by: U.ospanova, kind: 'STATUS', action: 'COMPLETE', text: 'Заменён термостат' },
      { h: 184, by: null, kind: 'STATUS', action: 'ACCEPT' },
    ],
  ),
]
