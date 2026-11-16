import type { StaffCandidate } from '@/api/requests'

/** Вагон заявки — один из вагонов сотрудника в бригаде рейса. */
export function onWagon(c: StaffCandidate, wagonNumber: string | null): boolean {
  return !!wagonNumber && (c.wagon?.number === wagonNumber || c.wagon2?.number === wagonNumber)
}

/**
 * Кому назначить заявку в порядке предпочтения (Ф-ЗАЯ-02): сначала сотрудники службы заявки, среди них и среди
 * остальных — сначала закреплённые за вагоном заявки; дальше — как пришло с сервера (по ФИО).
 *
 * @param wagonNumber номер вагона заявки; null — вагон не выбран
 */
export function sortStaff(list: StaffCandidate[], wagonNumber: string | null): StaffCandidate[] {
  const rank = (c: StaffCandidate) => (c.inService ? 0 : 2) + (onWagon(c, wagonNumber) ? 0 : 1)
  return [...list].sort((a, b) => rank(a) - rank(b))
}
