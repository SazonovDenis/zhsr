import type { TripResponsibles, WagonUsrs } from '@/api/trips'
import { t } from '@/boot/i18n'

/** Черновик ответственных: вагон (wagon.id) — id сотрудников. */
export type ResponsiblesDraft = Record<number, number[]>

/** Черновик из ответственных вагонов рейса: текущих, а до отправления — плана ответственных. */
export function currentDraft(data: TripResponsibles): ResponsiblesDraft {
  const res: ResponsiblesDraft = {}
  for (const w of data.wagons) res[w.wagon.id] = w.usrs.map((u) => u.id)
  return res
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
