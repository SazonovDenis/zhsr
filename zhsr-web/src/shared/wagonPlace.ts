import type { WagonRow } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { LocationType } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { tripRefText } from '@/shared/trips/tripRefText'

/**
 * Название места расположения вагона для показа: станция, депо, парк отстоя или рейс. Справочники Station, Depot и Yard
 * DictManager загружает сам при первом обращении; пока не загружены — показывается id.
 *
 * @param kind вид расположения — id справочника LocationKind
 * @param ref  место — id справочника по виду расположения; у рейса не нужен
 * @param trip рейс для показа: «002 • Алматы — Астана»
 */
export function placeText(kind: number, ref: number | null, trip: string | null = null): string {
  switch (kind) {
    case LocationType.STATION:
      return dictManager.getValue('Station', ref)
    case LocationType.DEPOT:
      return dictManager.getValue('Depot', ref)
    case LocationType.YARD:
      return dictManager.getValue('Yard', ref)
    case LocationType.TRIP:
      return t('Рейс {trip}', { trip: trip ?? '' })
    default:
      return ''
  }
}

/** Где вагон из списка вагонов сейчас. */
export function wagonPlace(w: WagonRow): string {
  const inTrip = w.locationType === LocationType.TRIP
  return placeText(w.locationType, inTrip ? null : w.place, w.trip ? tripRefText(w.trip) : null)
}
