import type { TripResponsibles } from '@/api/trips'
import type { UserRef } from '@/api/requests'
import { t } from '@/boot/i18n'
import { openDialog } from '@/shared/components/dialog/openDialog'
import type { UserPickRow } from '@/shared/components/dialog/UserPickDialog.vue'
import UserPickDialog from '@/shared/components/dialog/UserPickDialog.vue'
import type { ResponsiblesDraft } from '@/shared/trips/tripResponsibles'
import { candidateWagonWarning, currentDraft } from '@/shared/trips/tripResponsibles'
import { isMe } from '@/shared/user'
import { responsibleDuty } from '@/shared/wagons/responsible'
import { candidatePlace } from './candidatePlace'

/**
 * Окно «Ответственные за вагон N» рейса: сотрудники из тех, кого можно назначить (data.candidates), с переключателем
 * «Поездная бригада» / «Сотрудники станции». У проводника, которому этот вагон нарушит правило «один вагон или два
 * соседних» (с учётом draft — черновика ответственных), — предупреждение жирным справа. current — назначенные
 * сейчас; save — что сделать с выбором. Возвращает выбор; окно закрыто без выбора — undefined.
 */
export function pickWagonUsers(
  data: TripResponsibles,
  number: string,
  current: UserRef[],
  save: (usrs: number[]) => Promise<void>,
  draft: ResponsiblesDraft = currentDraft(data),
): Promise<{ usrs: number[] } | undefined> {
  const wagon = data.wagons.find((w) => w.wagon.number === number)!.wagon.id
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
          place: candidatePlace(c, candidateWagonWarning(data, draft, c.id, wagon)),
        })),
      ),
    save,
  })
}
