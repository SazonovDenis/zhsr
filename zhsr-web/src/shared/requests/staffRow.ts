import type { StaffCandidate } from '@/api/requests'
import { t } from '@/boot/i18n'
import type { UserPickRow } from '@/shared/components/dialog/UserPickDialog.vue'
import type { UserPlacePart } from '@/shared/components/UserOption.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { isMe } from '@/shared/user'
import { sortStaff } from './staff'

/**
 * Данные строки «Списка ФИО» (UserOption) для исполнителя заявки: должность в бригаде рейса, справа вагоны сотрудника.
 * Жирным — совпадения с заявкой: должность сотрудника службы заявки и вагон заявки wagonNumber. candidate = null —
 * сотрудник без должности и вагонов.
 */
export function staffRow(
  candidate: StaffCandidate | null,
  wagonNumber: string | null,
): { duty: string; dutyMatch: boolean; place: UserPlacePart[] } {
  if (!candidate) return { duty: '', dutyMatch: false, place: [] }
  const wagons = [candidate.wagon?.number, candidate.wagon2?.number].filter((n): n is string => !!n)
  return {
    duty: candidate.crewDutyType !== null ? dictManager.getValue('CrewDuty', candidate.crewDutyType) : '',
    dutyMatch: candidate.inService,
    place: wagons.map((w) => ({ text: t('ваг. {n}', { n: w }), match: w === wagonNumber })),
  }
}

/** Кому можно назначить заявку — сотрудники окна выбора (UserPickDialog): предпочтительные первыми (sortStaff). */
export function executorRows(staff: StaffCandidate[], wagonNumber: string | null): UserPickRow[] {
  return sortStaff(staff, wagonNumber).map((c) => ({
    id: c.usr.id,
    name: c.usr.name,
    me: isMe(c.usr),
    ...staffRow(c, wagonNumber),
  }))
}
