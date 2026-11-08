import type { TransferPlace } from '@/api/transfers'
import { LocationType } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'

/**
 * Расположение в акте приёма-передачи (Ф-АКТ-02): станция, депо или парк отстоя. Код расположения — одной строкой для
 * выпадающих списков и запроса страницы: s12 — станция, d3 — депо, y4 — парк отстоя. Справочники Station, Depot, Yard
 * и LocationKind должен загрузить вызывающий.
 */

const PREFIX: Record<string, keyof TransferPlace> = { s: 'station', d: 'depot', y: 'yard' }

/** Код расположения; ничего не заполнено — null. */
export function placeCode(p: TransferPlace): string | null {
  if (p.station !== null) return `s${p.station}`
  if (p.depot !== null) return `d${p.depot}`
  if (p.yard !== null) return `y${p.yard}`
  return null
}

/** Расположение по коду; неизвестный код или null — пустое расположение. */
export function placeOfCode(code: string | null | undefined): TransferPlace {
  const res: TransferPlace = { station: null, depot: null, yard: null }
  const field = code ? PREFIX[code.charAt(0)] : undefined
  const id = code ? Number(code.slice(1)) : NaN
  if (field && id) res[field] = id
  return res
}

/** Вид расположения — константы LocationType; пустое расположение — null. */
export function placeKind(p: TransferPlace): number | null {
  if (p.station !== null) return LocationType.STATION
  if (p.depot !== null) return LocationType.DEPOT
  if (p.yard !== null) return LocationType.YARD
  return null
}

/** Расположение вагона, если вагон стоит на станции, в депо или парке отстоя; в рейсе — null. */
export function wagonTransferPlace(locationType: number, place: number): TransferPlace | null {
  const res: TransferPlace = { station: null, depot: null, yard: null }
  if (locationType === LocationType.STATION) res.station = place
  else if (locationType === LocationType.DEPOT) res.depot = place
  else if (locationType === LocationType.YARD) res.yard = place
  else return null
  return res
}

const stationField = (name: 'Depot' | 'Yard', id: number) =>
  (dictManager.getRec(name, id) as { station?: number | null } | null)?.station ?? null

/** Станция расположения: сама станция или станция депо или парка отстоя; не задана — null. */
export function placeStation(p: TransferPlace): number | null {
  if (p.station !== null) return p.station
  if (p.depot !== null) return stationField('Depot', p.depot)
  if (p.yard !== null) return stationField('Yard', p.yard)
  return null
}

/** Название расположения: станции, депо или парка отстоя. */
export function placeName(p: TransferPlace): string {
  if (p.station !== null) return dictManager.getValue('Station', p.station)
  if (p.depot !== null) return dictManager.getValue('Depot', p.depot)
  if (p.yard !== null) return dictManager.getValue('Yard', p.yard)
  return ''
}

/** Расположение для показа: «Название • вид расположения». */
export function transferPlaceText(p: TransferPlace): string {
  const kind = placeKind(p)
  if (kind === null) return ''
  return `${placeName(p)} • ${dictManager.getValue('LocationKind', kind)}`
}

/** Расположения станции: сама станция, её депо и парки отстоя. */
export function stationPlaceList(station: number): TransferPlace[] {
  const at = (name: 'Depot' | 'Yard') =>
    dictManager.getList(name).filter((r) => (r as { station?: number | null }).station === station)
  return [
    { station, depot: null, yard: null },
    ...at('Depot').map((r) => ({ station: null, depot: r.id, yard: null })),
    ...at('Yard').map((r) => ({ station: null, depot: null, yard: r.id })),
  ]
}

/** Расположения станции — варианты выбора с кодом расположения и текстом «Название • вид расположения». */
export function stationPlaces(station: number): { id: string; text: string }[] {
  return stationPlaceList(station).map((p) => ({ id: placeCode(p)!, text: transferPlaceText(p) }))
}
