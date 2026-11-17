import type { UserRef } from '@/api/requests'
import { openDialog } from '@/shared/components/dialog/openDialog'
import UserPickDialog from '@/shared/components/dialog/UserPickDialog.vue'
import type { UserPickRow } from '@/shared/components/dialog/UserPickDialog.vue'
import { isMe } from '@/shared/user'
import { fetchResponsibles, responsibleDuty, responsiblePlace } from '@/shared/wagons/responsible'

/**
 * Назначение ответственного за вагоны в окне выбора сотрудника (UserPickDialog). Кандидаты — как у
 * ResponsiblePicker: сотрудники депо станции station, бригада рейса trip и бригады рейсов поезда вагонов wagons.
 * current — ответственный сейчас; reassign — ответственный уже назначен (заголовок и кнопка «Переназначить»). save —
 * сохранить выбор (id сотрудника, null — не назначен): сохраняет окно, ошибка — в окне. Результат — выбор после
 * успешного сохранения; окно закрыли — undefined.
 */
export async function pickResponsible(opts: {
  station: number | null
  trip?: number | null
  wagons?: string[]
  current: UserRef | null
  reassign: boolean
  save: (usr: number | null) => Promise<void>
}): Promise<{ usr: number | null } | undefined> {
  return openDialog<{ usr: number | null }>(UserPickDialog, {
    title: opts.reassign ? 'Переназначить ответственного' : 'Назначить ответственного',
    okText: opts.reassign ? 'Переназначить' : 'Назначить',
    current: opts.current,
    save: opts.save,
    load: async (): Promise<UserPickRow[]> =>
      (await fetchResponsibles(opts.station, opts.trip ?? null, opts.wagons ?? [])).map((c) => ({
        id: c.id,
        name: c.name,
        me: isMe(c),
        duty: responsibleDuty(c),
        place: responsiblePlace(c),
      })),
  })
}
