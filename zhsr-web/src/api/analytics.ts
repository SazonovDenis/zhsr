import { http } from './http'
import type { TripRef } from './wagons'
import type { StationPoint } from './stages'
import type { UserRef, WagonRef } from './requests'
import type { TrainRef } from './trains'

/** API раздела «Аналитика»: данные диаграмм. */

/** Строка диаграммы «переработка»: сотрудник и его рабочие часы по месяцам. */
export interface OvertimeRow {
  usr: UserRef
  /** обязанность в бригаде (CrewDuty): 1 — проводник, 2 — ЛНП */
  crewDutyType: number
  /** текущее депо сотрудника — id справочника Depot; null — депо нет */
  depot: number | null
  /** часы по месяцам, в порядке OvertimeChart.months */
  hours: number[]
}

/** Диаграмма «переработка»: рабочие часы бригад по месяцам относительно нормы. */
export interface OvertimeChart {
  /** начала месяцев (ISO со смещением): 11 полных месяцев и текущий, незаконченный */
  months: string[]
  /** норма часов в месяц */
  norm: number
  rows: OvertimeRow[]
}

/** Обязанность в бригаде «ЛНП» (CrewDuty.LNP). */
export const CREW_DUTY_LNP = 2

/** Рабочие часы проводников и ЛНП по месяцам за 12 месяцев, включая текущий. */
export async function fetchOvertime(): Promise<OvertimeChart> {
  return (await http.get<OvertimeChart>('/analytics/overtime')).data
}

/** Вагон диаграммы «давно не проверяли»: последние проверки и последний рейс. */
export interface UncheckedWagonRow {
  wagon: WagonRef
  /** депо приписки — id справочника Depot; null — не задано */
  depot: number | null
  /** завершение последнего чек-листа вагона; null — чек-листов не было */
  lastCheckDt: string | null
  /** завершение последней описи вагона; null — описей не было */
  lastEquipDt: string | null
  /** отправление последнего рейса вагона; null — рейсов не было */
  lastTripDt: string | null
}

/** Вагоны в эксплуатации с датами последних проверок и последнего рейса; at — по состоянию на момент, ISO с поясом. */
export async function fetchUncheckedWagons(at: string): Promise<UncheckedWagonRow[]> {
  return (await http.get<UncheckedWagonRow[]>('/analytics/unchecked-wagons', { params: { at } })).data
}

/** День диаграммы «Количество и сроки исполнения заявок». */
export interface RequestsDay {
  /** дата: 2026-11-23 */
  day: string
  /** поданные заявки */
  requests: number
  /** отправленные рейсы */
  trips: number
}

/** Неделя диаграммы «Количество и сроки исполнения заявок»: сроки исполнения заявок, поданных за неделю. */
export interface RequestsWeek {
  /** понедельник недели: 2026-11-23 */
  week: string
  /** число исполненных заявок */
  done: number
  /** сроки типичных заявок по возрастанию, часы: без 10% самых быстрых и 10% самых долгих */
  hours: number[]
  /** медиана срока всех исполненных заявок, часы; null — исполненных нет */
  median: number | null
}

/** Диаграмма «Количество и сроки исполнения заявок»: заявки по дням и сроки исполнения по неделям. */
export interface RequestsChart {
  /** дни без пропусков: с понедельника по сегодня */
  days: RequestsDay[]
  /** недели этих дней; последняя не закончена */
  weeks: RequestsWeek[]
}

/** Заявки и сроки исполнения за год; service — служба, null — все службы. */
export async function fetchRequestsChart(service: number | null): Promise<RequestsChart> {
  return (await http.get<RequestsChart>('/analytics/requests', { params: { service: service ?? undefined } })).data
}

/** Исполнитель диаграммы «Скорость и качество исполнения заявок» и его заявки. */
export interface ExecutionRow {
  executorUsr: UserRef
  /** текущее депо исполнителя — id справочника Depot; null — депо нет */
  depot: number | null
  /** минуты от взятия в работу до первого выполнения, по заявкам; не пуст */
  minutes: number[]
  /** число этих заявок, возвращённых на доработку */
  returned: number
}

/** Исполнители технической службы и время исполнения заявок за год. */
export async function fetchExecution(): Promise<ExecutionRow[]> {
  return (await http.get<ExecutionRow[]>('/analytics/execution')).data
}

/** Выполненные заявки одной службы, неисправности и депо исполнителя: диаграмма «Заявки». */
export interface WorkTimeRow {
  /** служба — id справочника Service */
  service: number
  /** неисправность — id справочника FaultType */
  faultType: number
  /** текущее депо исполнителя — id справочника Depot; null — исполнителя или депо нет */
  depot: number | null
  /** число выполненных заявок */
  requests: number
  /** минуты от взятия в работу до первого выполнения, сумма по заявкам */
  minutes: number
}

/** Выполненные заявки, поданные за период [from, to). */
export async function fetchWorkTime(from: string, to: string): Promise<WorkTimeRow[]> {
  return (await http.get<WorkTimeRow[]>('/analytics/work-time', { params: { from, to } })).data
}

/** Остановка маршрута на карте. */
export interface GeoStop {
  /** остановка (route_station.id) */
  id: number
  /** станция — id справочника Station */
  station: number
  /** поезд здесь останавливается; false — станция следования */
  haveStop: boolean
  /** километр линии маршрута */
  km: number
  /** прибытие по расписанию, минуты от отправления с начальной станции */
  arrivalMin: number
  /** отправление по расписанию, там же */
  departureMin: number
}

/** Маршрут на карте. */
export interface GeoRoute {
  id: number
  trainNumber: string
  name: string
  /** точки линии по порядку: долгота, широта, километр от начала линии */
  points: [number, number, number][]
  /** остановки по порядку */
  stops: GeoStop[]
}

/** Прицепка или отцепка вагона в цикле рейса. */
export interface GeoChange {
  /** train_wagon_change.id */
  id: number
  wagon: WagonRef
  /** слой — константы TrainWagonKind */
  kind: number
  /** вид — константы TrainWagonChangeType */
  changeType: number
  /** время записи, ISO */
  dt: string
  /** с какого времени запись действует: у ожидающего плана — не раньше последнего факта поезда */
  moment: string
  /** плановое время; null — факт без плана */
  planDt: string | null
  /** станция — id справочника Station */
  station: number
}

/**
 * Рейс на карте. Цикл рейса — рейс с подготовкой: записи прицепок и отцепок рейса и записи вне рейса после прибытия
 * предыдущего рейса поезда; записи после прибытия последнего рейса поезда — его же.
 */
export interface GeoTrip {
  id: number
  /** маршрут — id из GeoChart.routes */
  route: number
  train: TrainRef
  /** состояние — константы TripStatus */
  tripStatusType: number
  plannedDt: string
  /** null — рейс не отправлен */
  departedDt: string | null
  /** null — рейс не прибыл */
  arrivedDt: string | null
  /** плановое прибытие */
  arrival: string
  /** начало цикла — прибытие предыдущего рейса поезда; null — предыдущего рейса нет */
  cycleStart: string | null
  /** записи цикла: факты и ожидающие планы, по времени */
  changes: GeoChange[]
  /** проводники рейса */
  conductors: UserRef[]
  /**
   * ход поезда по порядку: минуты от начала периода, километр линии маршрута, отклонение от расписания в минутах
   * (больше нуля — отставание); пусто — координат нет
   */
  track: [number, number, number][]
}

/** Участие вагона в поезде. */
export interface GeoState {
  train: number
  wagon: number
  /** слой — константы TrainWagonKind */
  kind: number
  dbeg: string
  dend: string | null
}

/** Расположение вагона за период. */
export interface GeoLocation {
  wagon: number
  dbeg: string
  dend: string | null
  /** станция расположения: сама станция, станция депо или парка отстоя; у вагона в рейсе — null */
  station: number | null
  /** депо, в котором вагон; null — не в депо */
  depot: number | null
  /** парк отстоя, в котором вагон; null — не в парке отстоя */
  yard: number | null
  /** рейс, в котором вагон; null — вне рейса */
  trip: TripRef | null
}

/** Период ответственных за вагон: факт или ожидающий план. */
export interface GeoResponsible {
  wagon: number
  /** слой — константы WagonResponsibleKind */
  kind: number
  dbeg: string
  dend: string | null
  /** плановое время начала; null — факт вне стадии рейса */
  planDt: string | null
  /** проводники рейсов из состава ответственных — id; у плана может быть пусто */
  usrs: number[]
}

/** Данные карт «Текущие рейсы» и поезда. Время точек хода — минуты от from. */
export interface GeoChart {
  from: string
  routes: GeoRoute[]
  /** по отправлению, и неотправленные */
  trips: GeoTrip[]
  states: GeoState[]
  locations: GeoLocation[]
  responsibles: GeoResponsible[]
  /** точки станций */
  stations: StationPoint[]
}

/** Карта поезда: его рейсы с формирования по текущее время и неотправленные. */
export async function fetchTrainGeoChart(train: number): Promise<GeoChart> {
  return (await http.get<GeoChart>(`/trains/${train}/geo`)).data
}

/** «Текущие рейсы» за период [from, to); details — с данными циклов рейсов. */
export async function fetchGeoChart(from: string, to: string, details: boolean): Promise<GeoChart> {
  return (await http.get<GeoChart>('/analytics/geo', { params: { from, to, points: details } })).data
}
