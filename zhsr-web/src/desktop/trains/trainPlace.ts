import type { TrainRow } from '@/api/trains'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import { tripRefText } from '@/shared/trips/tripRefText'
import { placeText } from '@/shared/wagonPlace'

/**
 * Где вагоны поезда — как расположение в списке вагонов: в рейсе — «Рейс: 2/1 • Алматы — Астана»; все в одном месте —
 * вид и место («Депо: Вагонное депо Астана»); в разных местах — «в разных местах»; вагонов нет — пусто. Справочники
 * LocationKind, Station, Depot и Yard готовит страница.
 */
export function trainPlaceText(row: TrainRow): string {
  if (row.trip) return t('Рейс: {trip}', { trip: tripRefText(row.trip) })
  if (row.locationType !== null && row.place !== null) {
    return `${dictManager.getValue('LocationKind', row.locationType)}: ${placeText(row.locationType, row.place)}`
  }
  return row.wagons.length ? t('в разных местах') : ''
}
