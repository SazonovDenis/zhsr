import type { UserRef } from '@/api/requests'
import type { TripRef } from '@/api/wagons'
import { http } from '@/api/http'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import { tripRefText } from '@/shared/trips/tripRefText'

/**
 * Ответственный за вагон в форме: id сотрудника, NOT_ASSIGNED — явно «ответственный не назначен», null — ещё не
 * выбран.
 */
export type ResponsibleChoice = number | typeof NOT_ASSIGNED | null

/** Выбор «ответственный не назначен». */
export const NOT_ASSIGNED = 'none'

/** Ответственный для запроса: id сотрудника; не назначен (или не выбран) — null. */
export function responsibleUsr(choice: ResponsibleChoice): number | null {
  return typeof choice === 'number' ? choice : null
}

/** Ответственный из данных — выбор формы: null в данных — «не назначен». */
export function responsibleChoice(usr: UserRef | null): ResponsibleChoice {
  return usr ? usr.id : NOT_ASSIGNED
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
