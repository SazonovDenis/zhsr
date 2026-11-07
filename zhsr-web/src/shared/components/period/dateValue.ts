import { date } from 'quasar'

/**
 * Значение даты в выборе даты: снаружи — ISO («2026-10-30», с временем — «2026-10-30T21:00»), внутри календаря —
 * дата DD.MM.YYYY и время HH:mm по отдельности.
 */

export const DATE_FORMAT = 'DD.MM.YYYY'
export const ISO_DATE = 'YYYY-MM-DD'

/** ISO → дата DD.MM.YYYY и время HH:mm; пусто — null. */
export function splitIso(iso: string | null): [string | null, string | null] {
  if (!iso) return [null, null]
  const day = date.formatDate(date.extractDate(iso.slice(0, 10), ISO_DATE), DATE_FORMAT)
  return [day, iso.length > 10 ? iso.slice(11, 16) : null]
}

/** Дата и время → ISO. Дата без часового пояса: toISOString сдвинул бы её. Без withTime время не пишется. */
export function joinIso(day: string | null, time: string | null, withTime: boolean): string | null {
  if (!day) return null
  const iso = date.formatDate(date.extractDate(day, DATE_FORMAT), ISO_DATE)
  return withTime ? `${iso}T${time ?? '00:00'}` : iso
}
