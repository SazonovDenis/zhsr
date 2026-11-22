import type { UserRef } from '@/api/requests'
import { openDialog } from '@/shared/components/dialog/openDialog'
import UserPickDialog from '@/shared/components/dialog/UserPickDialog.vue'
import type { UserPickRow } from '@/shared/components/dialog/UserPickDialog.vue'
import { isMe } from '@/shared/user'
import { fetchResponsibles, responsibleDuty, responsiblePlace } from '@/shared/wagons/responsible'

/**
 * Назначение ответственных за вагон в окне выбора сотрудника с выбором нескольких (UserPickDialog, multiple). Кандидаты — как у
 * ResponsiblePicker: сотрудники депо станции station, бригада рейса trip и бригады рейсов поезда вагонов wagons.
 * current — ответственные сейчас (пусто — не назначены: заголовок и кнопка «Назначить», иначе «Сменить»). save —
 * сохранить выбор (id сотрудников, пусто — не назначены): сохраняет окно, ошибка — в окне. Результат — выбор после
 * успешного сохранения; окно закрыли — undefined.
 */
export async function pickResponsibles(opts: {
  station: number | null
  trip?: number | null
  wagons?: string[]
  current: UserRef[]
  save: (usrs: number[]) => Promise<void>
}): Promise<{ usrs: number[] } | undefined> {
  const change = opts.current.length > 0
  return openDialog<{ usrs: number[] }>(UserPickDialog, {
    multiple: true,
    title: change ? 'Сменить ответственных' : 'Назначить ответственных',
    okText: change ? 'Сменить' : 'Назначить',
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
