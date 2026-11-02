import { date } from 'quasar'

/**
 * Время для фиктивных данных относительно текущего момента, ISO без секунд: fakeTime(-2) — два часа назад. Рейс «в
 * пути» и свежие заявки остаются свежими при любом запуске. Нужно только заготовкам API, пока сервера нет.
 */
export function fakeTime(hours: number): string {
  return isoNow(date.addToDate(new Date(), { minutes: Math.round(hours * 60) }))
}

/** Момент в виде ISO без секунд, как время в данных сервера. */
export function isoNow(d = new Date()): string {
  return date.formatDate(d, 'YYYY-MM-DDTHH:mm')
}
