import { http } from './http'
import type { UserRef, WagonRef } from './requests'
import type { TripRef } from './wagons'

/**
 * API поездов: список, создание и обозначение, журнал поезда и участие вагонов (план и факт), прицепка, отцепка и
 * смена порядка вагонов, отметка и отмена плана, удаление ошибочного факта, занятость вагонов.
 */

/** Поезд в данных. */
export interface TrainRef {
  id: number
  /** обозначение поезда */
  name: string
}

/** Позиция вагона в новом порядке — строка train_wagon_seq_item. */
export interface TrainWagonSeqItem {
  wagon: WagonRef
  /** позиция в поезде, с 1 */
  seq: number
}

/**
 * Запись журнала поезда, план или факт: прицепка или отцепка вагона или смена порядка вагонов. Что за запись, говорит
 * code; запись определяют code и id; plan и fact — записи той же таблицы.
 */
export interface TrainChange {
  /** таблица записи — константы TrainJournalCode */
  code: string
  id: number
  train: TrainRef
  /** вагон прицепки или отцепки; у смены порядка null */
  wagon: WagonRef | null
  /** смена порядка: новый порядок вагонов по позициям; у прицепки и отцепки пусто */
  trainWagonSeqItem: TrainWagonSeqItem[]
  /** слой — константы TrainWagonKind */
  kind: number
  /** статус плана — константы TrainWagonPlanStatus; у факта null */
  planStatus: number | null
  /** вид — константы TrainWagonChangeType; у смены порядка null */
  changeType: number | null
  /** время, ISO: у плана плановое, у факта фактическое */
  dt: string
  /** позиция, на которую прицепляют вагон, с 1; у отцепки и смены порядка null */
  seq: number | null
  /** станция — id справочника Station */
  station: number
  /** рейс прицепки или отцепки; null — вне рейса */
  trip: TripRef | null
  /** остановка маршрута рейса (route_station.id); null — вне рейса */
  routeStation: number | null
  /** у факта: отработанный им план (id записи журнала); null — факт без плана */
  plan: number | null
  /** кто внёс */
  usr: UserRef
  /** основания, примечания */
  comment: string | null
  /** у факта по плану: плановое время, ISO */
  planDt: string | null
  /** у факта по плану: плановая станция — id справочника Station */
  planStation: number | null
  /** у отработанного плана: факт, который его отработал (id записи журнала); иначе null */
  fact: number | null
  /** у плана: плановое время прошло, план не отмечен */
  overdue: boolean
  /** текущий сотрудник может отметить план */
  canMark: boolean
  /** текущий сотрудник может отменить план */
  canCancel: boolean
  /** текущий сотрудник может удалить ошибочный факт (если факт последний у поезда) */
  canUndo: boolean
  /** у отменённого плана: текущий сотрудник может вернуть план */
  canRestore: boolean
  /** текущий сотрудник может исправить запись: ожидающий план — целиком, факт — основания */
  canEdit: boolean
}

/** Запись участия вагона в поезде: позиция вагона с периодом действия, слой — факт или ожидаемый состав. */
export interface TrainState {
  id: number
  /** вагон — wagon.id */
  wagon: number
  /** слой — константы TrainWagonKind */
  kind: number
  /** позиция в поезде, с 1 */
  seq: number
  /** начало периода, ISO */
  dbeg: string
  /** конец периода, ISO; null — запись открыта */
  dend: string | null
}

/** Вагон поезда с текущими расположением и состоянием. */
export interface TrainWagon {
  /** wagon.id */
  wagon: number
  number: string
  /** тип вагона — id справочника WagonType */
  wagonType: number
  /** модель вагона — id справочника WagonModel; null — не задана */
  modelType: number | null
  /** вид расположения — id справочника LocationKind (константы LocationType) */
  locationType: number | null
  /** место расположения по виду: станция, депо, парк отстоя или рейс */
  place: number | null
  /** станция расположения: сама станция, станция депо или парка отстоя; у вагона в рейсе — null */
  station: number | null
  /** рейс, в котором вагон сейчас; null — вагон не в рейсе */
  trip: TripRef | null
  /** состояние — id справочника WagonState */
  wagonStatusType: number
  /** с какого времени текущее состояние, ISO */
  dbegStatus: string
  /** текущая запись журнала ответственных (wagon_responsible.id); null — журнал ответственных пуст */
  wagonResponsible: number | null
  /** текущие ответственные за вагон, по ФИО; пусто — не назначены */
  responsibleUsrs: UserRef[]
  /** пассажирский вагон: норма проводников — только для пассажирских */
  passenger: boolean
}

/** Вагон в строке поезда: поля для колонок «Номер» и «Тип» списка вагонов. */
export interface TrainRowWagon {
  id: number
  number: string
  wagonType: number
  /** модель вагона — id справочника WagonModel; null — не задана */
  modelType: number | null
}

/** Строка таблицы поездов. */
export interface TrainRow {
  id: number
  name: string
  /** когда сформирован, ISO */
  dbeg: string
  /** когда расформирован, ISO; null — поезд действует */
  dend: string | null
  /** вагоны поезда сейчас по порядку: номер, тип и модель */
  wagons: TrainRowWagon[]
  /** станция вагонов поезда — id справочника Station; null — на разных станциях, в рейсе или вагонов нет */
  station: number | null
  /** вид расположения вагонов поезда (LocationKind): все в одном месте; null — в разных местах или вагонов нет */
  locationType: number | null
  /** место вагонов по виду расположения: станция, депо, парк отстоя или рейс; null — как у locationType */
  place: number | null
  /** число плановых прицепок и отцепок */
  plannedCount: number
  /** рейс поезда в статусе «выполняется» */
  trip: TripRef | null
  /** ближайший рейс поезда в статусе «план» */
  nextTrip: TripRef | null
  /** рейсы поезда в статусе «выполняется» и «план», видимые сотруднику, по плановому отправлению */
  trips: TripRef[]
}

/** Поезд с журналом прицепок и отцепок и участием вагонов: состав на любой момент берётся из states. */
export interface TrainCard extends TrainRow {
  /** журнал: факты и ожидающие планы, по времени */
  changes: TrainChange[]
  /** участие вагонов в поезде: оба слоя, вся история */
  states: TrainState[]
  /** вагоны из журнала и участия с текущими расположением и состоянием */
  trainWagons: TrainWagon[]
  /** время последнего факта поезда, ISO: раньше него факт не вносится, удалить можно только факт этого времени */
  lastFactDt: string | null
  /** рейсы в статусе «план», «выполняется» и «выполнен» — отметки отправления и прибытия в журнале */
  tripMarks: TrainTripMark[]
  /** текущий сотрудник может менять поезд */
  canEdit: boolean
  /** текущий сотрудник может прицеплять и отцеплять: кто меняет поезд, у поезда в рейсе — ещё ЛНП рейса и мастер депо */
  canCouple: boolean
}

/** Рейс поезда для отметок отправления и прибытия в журнале поезда. */
export interface TrainTripMark {
  trip: TripRef
  /** состояние рейса — константы TripStatus */
  tripStatusType: number
  /** плановое отправление, ISO */
  plannedDt: string
  /** фактическое отправление, ISO; null — рейс не отправлен */
  departedDt: string | null
  /** фактическое прибытие, ISO; null — рейс не прибыл */
  arrivedDt: string | null
  /** плановое прибытие, ISO */
  arrival: string
}

/** Поезд как вариант выбора в рейсе. */
export interface TrainOption extends TrainRow {
  /** рейс, в котором поезд занят во время выбранного рейса; null — свободен */
  busyTrip: TripRef | null
}

/** Занятость вагона в поезде. */
export interface WagonBusy {
  number: string
  train: TrainRef
  /** с какого времени вагон в поезде, ISO */
  dbeg: string
  /** до какого времени, ISO; null — без конца */
  dend: string | null
  /** рейс поезда в это время; null — поезд не в рейсе */
  trip: TripRef | null
}

export interface TrainFilter {
  /** состояния — константы ActiveState; пусто — все */
  states: number[]
  /** часть обозначения поезда или номера вагона; пусто — без поиска */
  search: string
}

/**
 * Прицепка или отцепка вагонов.
 * Одна операция над составом поезда, откуда бы её ни вызвали: рейс сервер определяет сам по поезду и остановке.
 * fact — факт (уже совершилась) или план; time — факт: фактическое время (null — сейчас), план: плановое время (на
 * остановке — время стоянки); station — вне рейса: станция; routeStation — остановка рейса поезда; seq — прицепка:
 * позиция первого вагона, вагоны встают подряд (null — в конец); responsibleUsrs — у факта в рейсе в статусе
 * «выполняется», пусто — не назначены.
 */
export interface TrainChangeBody {
  /** вагоны — id */
  wagons: number[]
  fact: boolean
  time: string | null
  station: number | null
  /** остановка рейса поезда (route_station.id): рейс сервер находит сам; null — вне рейса */
  routeStation: number | null
  seq: number | null
  comment: string
  responsibleUsrs: number[]
}

/**
 * Отметка исполнения плана: time — фактическое время (null — сейчас); station — фактическая станция плана вне рейса
 * (null — станция плана); seq — фактическая позиция прицепки (null — позиция плана); comment — основания, примечания факта
 * (null — как у плана); responsibleUsrs — в рейсе в статусе «выполняется».
 */
export interface MarkBody {
  time: string | null
  station: number | null
  seq: number | null
  comment: string | null
  responsibleUsrs: number[]
}

/**
 * Неготовность вагона к прицепке или к рейсу: занятость в другом поезде (train, trip, dbeg, dend, fact; на каждый поезд
 * своя запись) или расположение (locationType, place, station, needStation).
 */
export interface WagonProblem {
  wagon: WagonRef
  /** константы WagonProblemType */
  wagonProblemType: number
  /** когда нужен вагон, ISO */
  needDt: string
  /** где будет вагон: вид расположения (LocationType) и id по виду; null — неизвестно */
  locationType: number | null
  place: number | null
  /** станция расположения; null — вагон в рейсе */
  station: number | null
  /** на какой станции вагон нужен */
  needStation: number | null
  /** другой поезд */
  train: TrainRef | null
  /** рейс другого поезда в это время */
  trip: TripRef | null
  /** с какого времени вагон в другом поезде, ISO */
  dbeg: string | null
  /** запланированная отцепка от другого поезда, ISO; null — не запланирована */
  dend: string | null
  /** прицепка к другому поезду выполнена фактом */
  fact: boolean
}

/** Рейс или плановая прицепка вне рейса, у вагонов которых есть неготовность. */
export interface PlanProblem {
  /** плановое отправление рейса или плановое время прицепки, ISO */
  dt: string
  train: TrainRef
  /** null — прицепка вне рейса */
  trip: TripRef | null
  /** плановая прицепка вне рейса; null — рейс или сохраняемая новая прицепка */
  trainWagonChange: number | null
  problems: WagonProblem[]
}

/**
 * Прицепка или отцепка, которую пользователь собирается сохранить: поезд, вагоны (id), вид (TrainWagonChangeType), факт
 * или план, время (ISO), станция (null — у отцепки: станция, на которой вагон сейчас), остановка рейса (null — вне
 * рейса) и исправляемый или отмечаемый план (null — новая запись).
 */
export interface WagonProblemQuery {
  train: number
  wagons: number[]
  changeType: number
  fact: boolean
  time: string | null
  station: number | null
  routeStation: number | null
  plan: number | null
}

/** Что будет мешать планам вагонов, если сохранить прицепку или отцепку. */
export async function fetchWagonProblems(query: WagonProblemQuery): Promise<PlanProblem[]> {
  if (query.wagons.length === 0) return []
  const { train, ...params } = query
  return (await http.get<PlanProblem[]>(`/trains/${train}/wagon-problems`, { params })).data
}

/** Действие над записью журнала, перед которым нужны предупреждения: отмена плана, возврат плана, удаление факта. */
export type JournalAction = 'cancel' | 'restore' | 'undo'

/** Что будет мешать планам вагона после действия над записью журнала. */
export async function fetchJournalProblems(c: TrainJournalId, action: JournalAction): Promise<PlanProblem[]> {
  const url = `/train-journal/${c.code}/${c.id}/wagon-problems`
  return (await http.get<PlanProblem[]>(url, { params: { action } })).data
}

/** Список поездов. */
export async function fetchTrains(filter: TrainFilter): Promise<TrainRow[]> {
  return (await http.get<TrainRow[]>('/trains', { params: filter })).data
}

/** Действующие поезда для выбора в рейсе; from и to — время рейса (ISO), trip — сам рейс, null — новый. */
export async function fetchTrainOptions(
  from: string | null,
  to: string | null,
  trip: number | null,
): Promise<TrainOption[]> {
  return (await http.get<TrainOption[]>('/trains/options', { params: { from, to, trip } })).data
}

/** Поезд с вагонами и рейсами. */
export async function fetchTrain(id: number): Promise<TrainCard> {
  return (await http.get<TrainCard>(`/trains/${id}`)).data
}

/** Создать поезд без вагонов. */
export async function createTrain(name: string): Promise<TrainRow> {
  return (await http.post<TrainRow>('/trains', { name })).data
}

/** Исправить обозначение поезда. */
export async function renameTrain(id: number, name: string): Promise<TrainRow> {
  return (await http.put<TrainRow>(`/trains/${id}`, { name })).data
}

/** Прицепить вагоны к поезду фактом или планом. */
export async function attachWagons(id: number, body: TrainChangeBody): Promise<TrainCard> {
  return (await http.post<TrainCard>(`/trains/${id}/attach`, body)).data
}

/** Отцепить вагоны от поезда фактом или планом. */
export async function detachWagons(id: number, body: TrainChangeBody): Promise<TrainCard> {
  return (await http.post<TrainCard>(`/trains/${id}/detach`, body)).data
}

/**
 * Сменить порядок вагонов фактом или планом: body.wagons — все вагоны поезда в новом порядке; seq и responsibleUsrs не
 * используются.
 */
export async function seqWagons(id: number, body: TrainChangeBody): Promise<TrainCard> {
  return (await http.post<TrainCard>(`/trains/${id}/seq`, body)).data
}

/** Запись журнала поезда в адресе: код и id. */
export type TrainJournalId = Pick<TrainChange, 'code' | 'id'>

const journalUrl = (c: TrainJournalId) => `/train-journal/${c.code}/${c.id}`

/** Отметить исполнение плана: факт по плану. */
export async function markChange(c: TrainJournalId, body: MarkBody): Promise<TrainCard> {
  return (await http.post<TrainCard>(`${journalUrl(c)}/done`, body)).data
}

/** Отменить план. */
export async function cancelChangePlan(c: TrainJournalId): Promise<TrainCard> {
  return (await http.post<TrainCard>(`${journalUrl(c)}/cancel`)).data
}

/** Вернуть отменённый план: снова «ожидает». */
export async function restoreChangePlan(c: TrainJournalId): Promise<TrainCard> {
  return (await http.post<TrainCard>(`${journalUrl(c)}/restore`)).data
}

/**
 * Исправить запись журнала: ожидающий план — время, место, позицию и основания (wagons и fact не используются), факт —
 * только основания (comment). Ответ — поезд после изменения.
 */
export async function editTrainChange(c: TrainJournalId, body: TrainChangeBody): Promise<TrainCard> {
  return (await http.put<TrainCard>(journalUrl(c), body)).data
}

/** Удалить ошибочный факт без истории. */
export async function undoChange(c: TrainJournalId): Promise<TrainCard> {
  return (await http.delete<TrainCard>(journalUrl(c))).data
}

/** Занятость вагонов в поездах с времени from (ISO; null — сейчас). */
export async function fetchWagonBusy(wagons: number[], from: string | null): Promise<WagonBusy[]> {
  if (wagons.length === 0) return []
  return (await http.get<WagonBusy[]>('/trains/busy', { params: { wagons, from } })).data
}

/** Удалить поезд: на него ссылаются другие записи — ошибка 409 с причиной. */
export async function deleteTrain(id: number): Promise<void> {
  await http.delete(`/trains/${id}`)
}
