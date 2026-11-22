import type { UserRef } from '@/api/requests'
import type { TripRef } from '@/api/wagons'
import { http } from '@/api/http'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import { tripRefText } from '@/shared/trips/tripRefText'

/**
 * Ответственные за вагон в форме: id сотрудников (непустой список), NOT_ASSIGNED — явно «ответственный не назначен»,
 * null — ещё не выбраны.
 */
export type ResponsibleChoice = number[] | typeof NOT_ASSIGNED | null

/** Выбор «ответственный не назначен». */
export const NOT_ASSIGNED = 'none'

/** Ответственные для запроса: id сотрудников; не назначены (или не выбраны) — пусто. */
export function responsibleUsrs(choice: ResponsibleChoice): number[] {
  return Array.isArray(choice) ? choice : []
}

/** Ответственные из данных — выбор формы: пусто в данных — «не назначен». */
export function responsibleChoice(usrs: UserRef[]): ResponsibleChoice {
  return usrs.length > 0 ? usrs.map((u) => u.id) : NOT_ASSIGNED
}

/** Кого можно назначить ответственным: сотрудник и кто он — член бригады рейса поезда вагонов или сотрудник депо. */
export interface ResponsibleCandidate extends UserRef {
  /** должность в бригаде рейса — id справочника CrewDuty; null — не в бригаде */
  crewDutyType: number | null
  /** рейс, в бригаде которого сотрудник; null — не в бригаде */
  trip: TripRef | null
  /** сотрудник депо станции */
  depotStaff: boolean
}

/** Должность кандидата в бригаде рейса; не в бригаде — пусто. */
export function responsibleDuty(c: ResponsibleCandidate | null): string {
  return c && c.crewDutyType !== null ? dictManager.getValue('CrewDuty', c.crewDutyType) : ''
}

/**
 * Строка под ФИО в таблицах ответственных рейса (десктоп и телефон): должность в бригаде; не в бригаде — служба
 * («депо станции»); не известно — пусто.
 */
export function candidateDuty(c: ResponsibleCandidate): string {
  return c.crewDutyType !== null ? responsibleDuty(c) : responsiblePlace(c)
}

/** Где кандидат: «рейс 002 • Алматы — Астана» или «депо станции»; не известно — пусто. */
export function responsiblePlace(c: ResponsibleCandidate | null): string {
  if (!c) return ''
  if (c.trip) return t('рейс {trip}', { trip: tripRefText(c.trip) })
  return c.depotStaff ? t('депо станции') : ''
}

/** Кто кандидат: «ПЭМ • рейс 002 • Алматы — Астана» или «депо станции»; не известно — пусто. */
export function responsibleWho(c: ResponsibleCandidate): string {
  if (c.trip && c.crewDutyType !== null) {
    return `${dictManager.getValue('CrewDuty', c.crewDutyType)} • ${t('рейс {trip}', { trip: tripRefText(c.trip) })}`
  }
  return c.depotStaff ? t('депо станции') : ''
}

/**
 * Кого можно назначить ответственным: сотрудники депо станции station, бригада рейса trip и бригады рейсов поезда
 * вагонов wagons; сначала бригада, затем сотрудники депо. Всё пусто — пусто.
 */
export async function fetchResponsibles(
  station: number | null,
  trip: number | null,
  wagons: string[] = [],
): Promise<ResponsibleCandidate[]> {
  return (await http.get<ResponsibleCandidate[]>('/wagons/responsibles', { params: { station, trip, wagons } })).data
}
