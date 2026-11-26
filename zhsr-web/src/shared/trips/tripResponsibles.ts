import type { TripResponsibles, WagonUsrs } from '@/api/trips'
import { t } from '@/boot/i18n'
import { CrewDuty } from '@/shared/constants/systemDicts'
import { conductorWagonsError } from '@/shared/trips/crewNorm'
import { userName } from '@/shared/user'

/** Черновик ответственных: вагон (wagon.id) — id сотрудников. */
export type ResponsiblesDraft = Record<number, number[]>

/** Черновик из ответственных вагонов рейса: текущих, а до отправления — плана ответственных. */
export function currentDraft(data: TripResponsibles): ResponsiblesDraft {
  const res: ResponsiblesDraft = {}
  for (const w of data.wagons) res[w.wagon.id] = w.usrs.map((u) => u.id)
  return res
}

/** Позиции (в списке вагонов рейса) вагонов сотрудника по черновику. */
function draftPositions(data: TripResponsibles, draft: ResponsiblesDraft, usr: number): number[] {
  return data.wagons.flatMap((w, i) => (draft[w.wagon.id]?.includes(usr) ? [i] : []))
}

const isConductor = (data: TripResponsibles, usr: number) =>
  data.candidates.some((c) => c.id === usr && c.crewDutyType === CrewDuty.CONDUCTOR)

/**
 * Нарушение правила «один вагон или два соседних» у проводника с вагонами wagons (wagon.id); не проводник или правило
 * выполнено — пусто.
 */
export function pickedWagonsError(data: TripResponsibles, usr: number, wagons: number[]): string {
  if (!isConductor(data, usr)) return ''
  return conductorWagonsError(data.wagons.flatMap((w, i) => (wagons.includes(w.wagon.id) ? [i] : [])))
}

/** Нарушение правила у проводника по черновику; пусто — нарушения нет. */
export function draftConductorError(data: TripResponsibles, draft: ResponsiblesDraft, usr: number): string {
  return isConductor(data, usr) ? conductorWagonsError(draftPositions(data, draft, usr)) : ''
}

/** Предупреждение кандидату на вагон wagon: что будет нарушено, если назначить его на этот вагон; ничего — пусто. */
export function candidateWagonWarning(
  data: TripResponsibles,
  draft: ResponsiblesDraft,
  usr: number,
  wagon: number,
): string {
  if (!isConductor(data, usr)) return ''
  const positions = data.wagons.flatMap((w, i) => (w.wagon.id === wagon || draft[w.wagon.id]?.includes(usr) ? [i] : []))
  if (!conductorWagonsError(positions)) return ''
  return positions.length > 2 ? t('с этим вагоном — больше двух вагонов') : t('с этим вагоном — несоседние вагоны')
}

/** Все нарушения черновика строками «ФИО: нарушение (номера вагонов)»; wagon — только у проводников этого вагона. */
export function draftConductorWarnings(data: TripResponsibles, draft: ResponsiblesDraft, wagon?: number): string[] {
  return data.candidates.flatMap((c) => {
    if (wagon !== undefined && !draft[wagon]?.includes(c.id)) return []
    const error = draftConductorError(data, draft, c.id)
    if (!error) return []
    const numbers = draftPositions(data, draft, c.id).map((i) => data.wagons[i]!.wagon.number)
    return [`${userName(c)}: ${error} (${numbers.join(', ')})`]
  })
}

const sameSet = (a: number[], b: number[]) => a.length === b.length && a.every((x) => b.includes(x))

/** Изменённые вагоны черновика: где состав отличается от текущего. */
export function draftChanges(data: TripResponsibles, draft: ResponsiblesDraft): WagonUsrs[] {
  return data.wagons
    .filter(
      (w) =>
        !sameSet(
          draft[w.wagon.id] ?? [],
          w.usrs.map((u) => u.id),
        ),
    )
    .map((w) => ({ wagon: w.wagon.id, usrs: draft[w.wagon.id] ?? [] }))
}

/**
 * Время факта ответственных — значение DatePicker (время браузера без пояса): не в будущем и не раньше чем сутки назад,
 * как проверяет сервер. Возвращает текст ошибки; пусто — время верно.
 */
export function factTimeError(time: string | null): string {
  if (!time) return t('Укажите время изменения')
  const at = new Date(time).getTime()
  const now = Date.now()
  // минута — погрешность часов, как на сервере
  if (at > now + 60_000) return t('Время факта не может быть в будущем')
  if (at < now - 24 * 3600_000) return t('Время факта не может быть раньше чем сутки назад')
  return ''
}
