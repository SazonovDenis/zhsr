import { http } from './http'
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

/** Маршрут на карте. */
export interface GeoRoute {
  id: number
  trainNumber: string
  name: string
  /** точки линии по порядку: долгота, широта, километр от начала линии */
  points: [number, number, number][]
}

/** Рейс на карте. */
export interface GeoTrip {
  id: number
  /** маршрут — id из GeoChart.routes */
  route: number
  train: TrainRef
  plannedDt: string
  departedDt: string
  /** null — рейс в пути */
  arrivedDt: string | null
  /**
   * ход поезда по порядку: минуты от начала периода, километр линии маршрута, отклонение от расписания в минутах
   * (больше нуля — отставание); пусто — координат нет
   */
  track: [number, number, number][]
}

/** Положения на карте вне рейсов: общее вагона и проводника. */
export interface GeoPlaces {
  /**
   * точки по времени вне рейсов и последняя точка каждого рейса: минуты от начала периода (меньше нуля — до периода),
   * долгота, широта; не пуст
   */
  points: [number, number, number][]
  /** рейсы периода: минуты начала и конца; конец null — рейс идёт */
  trips: [number, number | null][]
  /** до какой минуты показывается; null — в последней точке бессрочно */
  until: number | null
}

/** Положения вагона на карте. */
export interface GeoWagon extends GeoPlaces {
  wagon: WagonRef
}

/** Положения проводника на карте. */
export interface GeoUsr extends GeoPlaces {
  usr: UserRef
}

/** Диаграмма «Текущие рейсы». Время точек — минуты от from. */
export interface GeoChart {
  from: string
  routes: GeoRoute[]
  trips: GeoTrip[]
  /** без points — пустой список */
  wagons: GeoWagon[]
  /** без points — пустой список */
  usrs: GeoUsr[]
}

/** Ход рейсов и положения вагонов и проводников за период [from, to); points — с положениями вагонов и проводников. */
export async function fetchGeoChart(from: string, to: string, points: boolean): Promise<GeoChart> {
  return (await http.get<GeoChart>('/analytics/geo', { params: { from, to, points } })).data
}
