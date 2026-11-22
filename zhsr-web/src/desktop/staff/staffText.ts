import type { WagonRef } from '@/api/requests'
import type { StaffRow } from '@/api/staff'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { tripRefText } from '@/shared/trips/tripRefText'

/** Вагоны строкой: «ваг. 12, 13»; вагонов нет — пусто. */
export function wagonsText(wagons: WagonRef[]): string {
  return wagons.length ? t('ваг. {list}', { list: wagons.map((w) => w.number).join(', ') }) : ''
}

/** Где сотрудник сейчас, первой строкой: в рейсе — рейс, иначе станция текущего депо; ничего — тире. */
export function factMain(row: StaffRow): string {
  if (row.factTrip) return t('Рейс {trip}', { trip: tripRefText(row.factTrip) })
  if (row.station !== null) return dictManager.getValue('Station', row.station)
  return row.depot !== null ? dictManager.getValue('Depot', row.depot) : '—'
}

/** Где сотрудник сейчас, второй строкой: в рейсе — должность и вагоны, иначе депо и вагоны по факту. */
export function factSub(row: StaffRow): string {
  const parts = row.factTrip
    ? [row.factDuty !== null ? dictManager.getValue('CrewDuty', row.factDuty) : '', wagonsText(row.factWagons)]
    : [
        row.station !== null && row.depot !== null ? dictManager.getValue('Depot', row.depot) : '',
        wagonsText(row.factWagons),
      ]
  return parts.filter(Boolean).join(' • ')
}

/** Ближайшее назначение первой строкой: рейс; не назначен — тире. */
export function planMain(row: StaffRow): string {
  return row.planTrip ? tripRefText(row.planTrip) : '—'
}

/** Ближайшее назначение второй строкой: отправление, должность, вагоны по плану. */
export function planSub(row: StaffRow): string {
  if (!row.planTrip) return ''
  return [
    formatDateTime(row.planTrip.plannedDt),
    row.planDuty !== null ? dictManager.getValue('CrewDuty', row.planDuty) : '',
    wagonsText(row.planWagons),
  ]
    .filter(Boolean)
    .join(' • ')
}

/** Текущее расположение сотрудника для подписи в окне перемещения: станция и депо. */
export function placeLabel(row: StaffRow): string {
  if (row.depot === null) return t('депо не задано')
  const depot = dictManager.getValue('Depot', row.depot)
  return row.station !== null ? `${dictManager.getValue('Station', row.station)} • ${depot}` : depot
}
