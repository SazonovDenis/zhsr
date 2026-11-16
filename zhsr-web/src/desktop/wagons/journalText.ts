import type { WagonJournalRow, WagonPlace } from '@/api/wagons'
import { JournalBasis } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { textOf } from '@/shared/checks/texts'
import { LocationType } from '@/shared/constants/systemDicts'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import { placeText } from '@/shared/wagonPlace'

/** Расположение из журнала для показа: «Астана-1 • станция», вагон в рейсе — «Рейс 002 … от дд.мм». */
export function journalPlace(p: WagonPlace): string {
  if (p.locationType === LocationType.TRIP)
    return placeText(p.locationType, null, p.trip ? tripRefDatedText(p.trip) : null)
  return placeText(p.locationType, p.place)
}

/** Основание записи журнала словами: «Отцепка в рейсе …», «Отправление рейса …», «Ввод вагона». */
export function basisText(row: WagonJournalRow): string {
  const trip = row.basisTrip ? tripRefDatedText(row.basisTrip) : ''
  switch (row.basisType) {
    case JournalBasis.ENTRY:
      return t('Ввод вагона')
    case JournalBasis.MANUAL:
      return t('Ручная правка')
    case JournalBasis.TRANSFER:
      return t('Акт приёма-передачи {n}', { n: row.actNumber ?? '' })
    case JournalBasis.DETACH:
      return t('Отцепка в рейсе {trip}', { trip })
    case JournalBasis.ATTACH:
      return t('Прицепка в рейсе {trip}', { trip })
    case JournalBasis.DEPARTURE:
      return t('Отправление рейса {trip}', { trip })
    case JournalBasis.ARRIVAL:
      return t('Прибытие рейса {trip}', { trip })
    case JournalBasis.CHECK:
      return t('Проверка «{name}», нарушений: {n}', {
        name: textOf(row.checkListTemplate),
        n: row.violationCount ?? 0,
      })
    case JournalBasis.EQUIP:
      return t('Опись «{name}», нарушений: {n}', {
        name: textOf(row.equipListTemplate),
        n: row.violationCount ?? 0,
      })
    default:
      return ''
  }
}
