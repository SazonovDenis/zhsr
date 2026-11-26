import type { TripResponsibles } from '@/api/trips'
import type { UserRef } from '@/api/requests'
import { t } from '@/boot/i18n'
import { openDialog } from '@/shared/components/dialog/openDialog'
import type { UserPickRow } from '@/shared/components/dialog/UserPickDialog.vue'
import UserPickDialog from '@/shared/components/dialog/UserPickDialog.vue'
import { isMe } from '@/shared/user'
import { responsibleDuty, responsiblePlace } from '@/shared/wagons/responsible'

/**
 * Окно «Ответственные за вагон N» рейса: сотрудники из тех, кого можно назначить (data.candidates), с переключателем
 * «Поездная бригада» / «Сотрудники станции». current — назначенные сейчас; save — что сделать с выбором. Возвращает
 * выбор; окно закрыто без выбора — undefined.
 */
export function pickWagonUsers(
  data: TripResponsibles,
  number: string,
  current: UserRef[],
  save: (usrs: number[]) => Promise<void>,
): Promise<{ usrs: number[] } | undefined> {
  // члены бригады рейса; остальные кандидаты — сотрудники станции
  const crew = new Set(data.candidates.filter((c) => c.crewDutyType !== null).map((c) => c.id))
  return openDialog<{ usrs: number[] }>(UserPickDialog, {
    multiple: true,
    title: t('Ответственные за вагон {n}', { n: number }),
    // переключатель «Поездная бригада» / «Сотрудники станции» — всегда: нет сотрудников станции — список пуст
    groups: [
      {
        id: 'conductors',
        text: 'Поездная бригада',
        filter: (r: UserPickRow) => crew.has(r.id),
        empty: 'Бригада рейса не назначена',
      },
      { id: 'station', text: 'Сотрудники станции', filter: (r: UserPickRow) => !crew.has(r.id) },
    ],
    okText: 'Выбрать',
    current,
    load: () =>
      Promise.resolve(
        data.candidates.map((c) => ({
          id: c.id,
          name: c.name,
          me: isMe(c),
          duty: responsibleDuty(c),
          place: responsiblePlace(c),
        })),
      ),
    save,
  })
}
