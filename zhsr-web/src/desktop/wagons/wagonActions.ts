import type { WagonRow } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { LocationType, RoleType } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { roleReason } from '@/shared/roleReason'
import { placeCode, placeKind, placeStation, stationPlaceList, wagonTransferPlace } from '@/shared/transferPlace'

/**
 * Действия с вагоном и списком вагонов — общие для панели вагона и меню строки списка: те же пункты и те же пояснения,
 * почему действие недоступно. Справочники Station, Depot и Yard загружает страница.
 */

/** Передача вагона в расположение вида kind (Ф-АКТ-02). */
export interface WagonTransfer {
  kind: number
  /** текст кнопки на русском */
  label: string
  /** запрос страницы акта; null — передать нельзя */
  query: Record<string, string> | null
  /** почему передать нельзя (переведено); пусто — можно */
  reason: string
}

/** Виды передачи по порядку кнопок: куда и текст кнопки на русском. */
export const TRANSFER_TARGETS = [
  { kind: LocationType.STATION, label: 'Передать на станцию' },
  { kind: LocationType.DEPOT, label: 'Передать в депо' },
  { kind: LocationType.YARD, label: 'Передать в парк отстоя' },
]

// где вагон уже стоит — пояснение к кнопке того же вида расположения
const ALREADY: Record<number, string> = {
  [LocationType.STATION]: 'Вагон уже на станции',
  [LocationType.DEPOT]: 'Вагон уже в депо',
  [LocationType.YARD]: 'Вагон уже в парке отстоя',
}
// на станции нет расположения, куда ведёт кнопка
const MISSING: Record<number, string> = {
  [LocationType.DEPOT]: 'На станции {station} нет депо',
  [LocationType.YARD]: 'На станции {station} нет парка отстоя',
}

/**
 * Передачи вагона: «на станцию», «в депо», «в парк отстоя». Откуда — где вагон стоит; куда — выбрано, если на станции
 * вагона одно такое расположение. Нельзя — пояснение: нет прав, вагон в рейсе, вагон уже там или на станции нет
 * такого расположения. Передают приёмщик и мастер депо; администратор может всё.
 */
export function wagonTransfers(w: WagonRow): WagonTransfer[] {
  const rights = roleReason([RoleType.RECEIVER, RoleType.MASTER], 'Передачу оформляет мастер депо или приёмщик')
  const from = wagonTransferPlace(w.locationType, w.place)
  const station = from && placeStation(from)
  const places = station ? stationPlaceList(station) : []
  return TRANSFER_TARGETS.map((b) => {
    const blocked = (reason: string) => ({ ...b, query: null, reason })
    if (rights) return blocked(rights)
    if (!from) return blocked(t('Вагон в рейсе'))
    if (!station) return blocked(t('У депо или парка отстоя вагона не задана станция'))
    // куда — другие расположения этого вида на станции вагона
    const targets = places.filter((p) => placeKind(p) === b.kind && placeCode(p) !== placeCode(from))
    if (!targets.length) {
      return blocked(
        b.kind === placeKind(from)
          ? t(ALREADY[b.kind]!)
          : t(MISSING[b.kind]!, { station: dictManager.getValue('Station', station) }),
      )
    }
    const to = targets.length === 1 ? { to: placeCode(targets[0]!)! } : {}
    return { ...b, query: { from: placeCode(from)!, ...to, wagons: w.number }, reason: '' }
  })
}

/** Почему нельзя ввести вагон (Ф-ВАГ-01): вводит администратор; пусто — можно. */
export function addWagonReason(): string {
  return roleReason([], 'Вводит администратор')
}
