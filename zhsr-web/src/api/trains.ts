import { http } from './http'
import type { UserRef, WagonRef } from './requests'
import type { TripRef } from './wagons'

/**
 * API поездов: список, создание и обозначение, журнал прицепок и отцепок и участие вагонов (план и факт), прицепка и
 * отцепка, отметка и отмена плана, удаление ошибочного факта, занятость вагонов.
 */

/** Поезд в данных. */
export interface TrainRef {
  id: number
  /** обозначение поезда */
  name: string
}

/** Прицепка или отцепка вагона — запись журнала поезда: план или факт. */
export interface TrainChange {
  id: number
  train: TrainRef
  wagon: WagonRef
  /** слой — константы TrainWagonKind */
  kind: number
  /** статус плана — константы TrainWagonPlanStatus; у факта null */
  planStatus: number | null
  /** вид — константы TrainWagonChangeType */
  changeType: number
  /** время, ISO: у плана плановое, у факта фактическое */
  dt: string
  /** позиция, на которую прицепляют вагон, с 1; у отцепки null */
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
  locationType: number
  /** место расположения по виду: станция, депо, парк отстоя или рейс */
  place: number
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
  /** рейсы в статусе «план» и «выполняется» */
  trips: TripRef[]
  /** текущий сотрудник может менять поезд */
  canEdit: boolean
  /** текущий сотрудник может прицеплять и отцеплять: кто меняет поезд, у поезда в рейсе — ещё ЛНП рейса и мастер депо */
  canCouple: boolean
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
  /** active — действующие, closed — расформированные; пусто — все */
  states: string[]
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
  wagons: string[]
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

/** Отметить исполнение плана прицепки или отцепки: факт по плану. */
export async function markChange(id: number, body: MarkBody): Promise<TrainCard> {
  return (await http.post<TrainCard>(`/train-changes/${id}/done`, body)).data
}

/** Отменить план прицепки или отцепки. */
export async function cancelChangePlan(id: number): Promise<TrainCard> {
  return (await http.post<TrainCard>(`/train-changes/${id}/cancel`)).data
}

/** Вернуть отменённый план прицепки или отцепки: снова «ожидает». */
export async function restoreChangePlan(id: number): Promise<TrainCard> {
  return (await http.post<TrainCard>(`/train-changes/${id}/restore`)).data
}

/** Удалить ошибочный факт прицепки или отцепки без истории. */
export async function undoChange(id: number): Promise<TrainCard> {
  return (await http.delete<TrainCard>(`/train-changes/${id}`)).data
}

/** Занятость вагонов в поездах с времени from (ISO; null — сейчас). */
export async function fetchWagonBusy(wagons: string[], from: string | null): Promise<WagonBusy[]> {
  if (wagons.length === 0) return []
  return (await http.get<WagonBusy[]>('/trains/busy', { params: { wagons, from } })).data
}

/** Удалить поезд: на него ссылаются другие записи — ошибка 409 с причиной. */
export async function deleteTrain(id: number): Promise<void> {
  await http.delete(`/trains/${id}`)
}
