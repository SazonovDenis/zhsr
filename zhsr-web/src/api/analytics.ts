import { http } from './http'
import type { UserRef, WagonRef } from './requests'

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

/** День диаграммы «Заявки и неисправности». */
export interface RequestsDay {
  /** дата: 2026-11-23 */
  day: string
  /** поданные заявки */
  requests: number
  /** отправленные рейсы */
  trips: number
}

/** Неделя диаграммы «Заявки и неисправности»: сроки исполнения заявок, поданных за неделю. */
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

/** Диаграмма «Заявки и неисправности»: заявки по дням и сроки исполнения по неделям. */
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
