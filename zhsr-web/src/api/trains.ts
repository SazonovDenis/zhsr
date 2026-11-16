import { http } from './http'
import type { UserRef } from './requests'
import type { TripRef } from './wagons'

/**
 * API поездов: список, создание и обозначение, состав поезда, прицепка и отцепка фактом и планом, отметка и отмена
 * плана, отмена выполненной прицепки и отцепки, занятость вагонов.
 */

/** Поезд в данных. */
export interface TrainRef {
  id: number
  /** обозначение поезда */
  name: string
}

/** Прицепка или отцепка вагона. */
export interface TrainChange {
  id: number
  /** участие вагона в поезде (train_wagon) */
  trainWagon: number
  /** вид — константы TrainWagonChangeType */
  changeType: number
  /** статус — константы TrainWagonChangeStatus */
  status: number
  /** плановое время, ISO; null — не планировалось */
  plannedDt: string | null
  /** фактическое время, ISO; null — не выполнено */
  factDt: string | null
  /** станция — id справочника Station */
  station: number
  /** рейс прицепки или отцепки; null — вне рейса */
  trip: TripRef | null
  /** остановка маршрута рейса (route_station.id); null — вне рейса */
  routeStation: number | null
  /** кто внёс */
  usr: UserRef
  /** основания, примечания */
  comment: string | null
  /** номер вагона */
  number: string
  /** текущий сотрудник может отметить план выполненным или отменить план */
  canMark: boolean
  /** текущий сотрудник может отменить выполненное изменение без истории */
  canUndo: boolean
}

/** Вагон поезда: участие вагона в поезде с текущим расположением. */
export interface TrainWagon {
  /** train_wagon.id */
  id: number
  /** wagon.id */
  wagon: number
  number: string
  /** тип вагона — id справочника WagonType */
  wagonType: number
  /** позиция в поезде */
  seq: number
  /** дата прицепки, ISO; null — прицепка запланирована */
  dbeg: string | null
  /** дата отцепки, ISO; null — вагон в поезде */
  dend: string | null
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
  /** пассажирский вагон: норма проводников — только для пассажирских */
  passenger: boolean
  attachPlan: TrainChange | null
  detachPlan: TrainChange | null
  attached: TrainChange | null
  detached: TrainChange | null
}

/** Строка таблицы поездов. */
export interface TrainRow {
  id: number
  name: string
  /** когда сформирован, ISO */
  dbeg: string
  /** когда расформирован, ISO; null — поезд действует */
  dend: string | null
  /** номера вагонов поезда сейчас по порядку */
  wagons: string[]
  /** станция вагонов поезда — id справочника Station; null — на разных станциях, в рейсе или вагонов нет */
  station: number | null
  /** число плановых прицепок и отцепок */
  plannedCount: number
  /** рейс поезда в статусе «выполняется» */
  trip: TripRef | null
  /** ближайший рейс поезда в статусе «план» */
  nextTrip: TripRef | null
}

/** Поезд с вагонами и рейсами. */
export interface TrainCard extends TrainRow {
  /** вагоны сейчас и с плановой прицепкой, по порядку */
  trainWagons: TrainWagon[]
  /** рейсы в статусе «план» и «выполняется» */
  trips: TripRef[]
  /** текущий сотрудник может менять поезд */
  canEdit: boolean
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
 * fact — факт (уже совершилась) или план; time — факт: фактическое время (null — сейчас), план: плановое время (в рейсе
 * null — время стоянки на остановке); station — вне рейса: станция; trip и routeStation — рейс поезда и остановка его
 * маршрута; responsibleUsr — у факта в рейсе в статусе «выполняется», null — не назначен.
 */
export interface TrainChangeBody {
  wagons: string[]
  fact: boolean
  time: string | null
  station: number | null
  trip: number | null
  routeStation: number | null
  comment: string
  responsibleUsr: number | null
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

/** Порядок вагонов поезда: номера по порядку. */
export async function orderTrain(id: number, wagons: string[]): Promise<TrainCard> {
  return (await http.put<TrainCard>(`/trains/${id}/order`, wagons)).data
}

/** Прицепить вагоны к поезду фактом или планом. */
export async function attachWagons(id: number, body: TrainChangeBody): Promise<TrainCard> {
  return (await http.post<TrainCard>(`/trains/${id}/attach`, body)).data
}

/** Отцепить вагоны от поезда фактом или планом. */
export async function detachWagons(id: number, body: TrainChangeBody): Promise<TrainCard> {
  return (await http.post<TrainCard>(`/trains/${id}/detach`, body)).data
}

/** Отметить плановую прицепку или отцепку выполненной; time — ISO, null — сейчас. */
export async function markChange(id: number, time: string | null, responsibleUsr: number | null): Promise<TrainCard> {
  return (await http.post<TrainCard>(`/train-changes/${id}/done`, { time, responsibleUsr })).data
}

/** Отменить план прицепки или отцепки. */
export async function cancelChangePlan(id: number): Promise<TrainCard> {
  return (await http.post<TrainCard>(`/train-changes/${id}/cancel`)).data
}

/** Отменить выполненную прицепку или отцепку без истории. */
export async function undoChange(id: number): Promise<TrainCard> {
  return (await http.delete<TrainCard>(`/train-changes/${id}`)).data
}

/** Занятость вагонов в поездах с времени from (ISO; null — сейчас). */
export async function fetchWagonBusy(wagons: string[], from: string | null): Promise<WagonBusy[]> {
  if (wagons.length === 0) return []
  return (await http.get<WagonBusy[]>('/trains/busy', { params: { wagons, from } })).data
}
