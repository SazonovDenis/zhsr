import { date } from 'quasar'

import type { PlanProblem, WagonProblem } from '@/api/trains'
import { t } from '@/boot/i18n'
import { WagonProblemType } from '@/shared/constants/WagonProblemType'
import { LocationType } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import { placeText } from '@/shared/wagonPlace'
import type { Phrase } from './phrase'

/** Неготовность вагона словами. Справочники Station, Depot, Yard и LocationKind должен загрузить вызывающий. */
export function wagonProblemText(p: WagonProblem): string {
  const detail = wagonProblemDetail(p)
  const text =
    p.wagonProblemType === WagonProblemType.YARD ? t('нужна передача на станцию: {place}', { place: detail }) : detail
  return t('Вагон {n}: {text}', { n: p.wagon.number, text })
}

/**
 * Предупреждение перед сохранением без номера вагона: «будет на станции …, а нужен будет на станции … 26.11 в 09:35,
 * для рейса 655/656.» — где окажется вагон, где, когда и для чего вагон нужен. Станции — жирным, рейс — ссылкой. Время
 * отправления рейса — после номера, только если отличается от времени, когда нужен вагон. Справочники — как у
 * wagonProblemText.
 */
export function planProblemPhrase(row: PlanProblem, p: WagonProblem): Phrase {
  const station = (id: number | null): Phrase => ({
    key: 'на станции {station}',
    params: { station: { bold: dictManager.getValue('Station', id) } },
  })
  const dayTime = (dt: string): Phrase => ({
    key: '{date} в {time}',
    params: { date: date.formatDate(dt, 'DD.MM'), time: date.formatDate(dt, 'HH:mm') },
  })
  const busy = p.wagonProblemType === WagonProblemType.BUSY
  let where: Phrase
  if (busy) {
    const detach = p.dend
      ? t('отцепка запланирована на {time}', { time: formatDateTime(p.dend) })
      : t('отцепка не запланирована')
    where = { key: 'в поезде {train} ({detach})', params: { train: p.train!.name, detach } }
  } else if (p.wagonProblemType === WagonProblemType.STATION) {
    where = p.station === null ? t('в рейсе') : station(p.station)
  } else {
    where = {
      key: p.locationType === LocationType.DEPOT ? 'в депо: {place}' : 'в парке отстоя: {place}',
      params: { place: placeText(p.locationType, p.place) },
    }
  }
  let purpose: Phrase
  if (row.trip) {
    const link = { trip: row.trip.id, train: row.train.id, text: row.trip.trainNumber }
    const sameTime = date.getDateDiff(row.trip.plannedDt, p.needDt, 'minutes') === 0
    purpose = {
      key: 'для рейса {trip}',
      params: {
        trip: sameTime
          ? link
          : { key: '{number} от {when}', params: { number: link, when: dayTime(row.trip.plannedDt) } },
      },
    }
  } else if (busy) purpose = t('для прицепки вне рейса')
  else purpose = { key: 'для прицепки к поезду {train} вне рейса', params: { train: row.train.name } }
  return {
    key: 'будет {where}, а нужен будет {target} {when}, {purpose}.',
    params: {
      where,
      target: busy ? { key: 'в поезде {train}', params: { train: row.train.name } } : station(p.needStation),
      when: dayTime(p.needDt),
      purpose,
    },
  }
}

/** Подробности неготовности без номера вагона — для ячейки в строке вагона. Справочники — как у wagonProblemText. */
export function wagonProblemDetail(p: WagonProblem): string {
  if (p.wagonProblemType === WagonProblemType.BUSY) {
    const trip = p.trip ? `, ${t('рейс {trip}', { trip: tripRefDatedText(p.trip) })}` : ''
    const attach = t(
      p.fact ? 'поезд {train}, прицепка выполнена {time}' : 'поезд {train}, прицепка запланирована на {time}',
      { train: `${p.train!.name}${trip}`, time: formatDateTime(p.dbeg) },
    )
    const detach = p.dend
      ? t('отцепка запланирована на {time}', { time: formatDateTime(p.dend) })
      : t('отцепка не запланирована')
    return `${attach}, ${detach}`
  }
  const station = (id: number | null) => dictManager.getValue('Station', id)
  if (p.wagonProblemType === WagonProblemType.STATION) {
    const need = station(p.needStation)
    return p.station === null
      ? t('будет в рейсе, а нужен на станции {need}: требуется перегоночный рейс', { need })
      : t('будет на станции {station}, а нужен на станции {need}: требуется перегоночный рейс', {
          station: station(p.station),
          need,
        })
  }
  return `${placeText(p.locationType, p.place)} • ${dictManager.getValue('LocationKind', p.locationType)}`
}
