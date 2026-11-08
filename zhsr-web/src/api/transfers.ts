import { isoDateTime } from '@/shared/format'
import type { UserRef } from './requests'
import type { WagonRow } from './wagons'
import { http } from './http'

/** API актов приёма-передачи вагонов между депо, станцией и парком отстоя (Ф-АКТ-02). */

/** Расположение в акте: откуда или куда; заполнено ровно одно из полей. */
export interface TransferPlace {
  /** станция — id справочника Station */
  station: number | null
  /** депо — id справочника Depot */
  depot: number | null
  /** парк отстоя — id справочника Yard */
  yard: number | null
}

/** Акт приёма-передачи. */
export interface Transfer {
  id: number
  number: string
  /** откуда: ровно одно из fromStation, fromDepot, fromYard — id справочников Station, Depot, Yard */
  fromStation: number | null
  fromDepot: number | null
  fromYard: number | null
  /** куда: ровно одно из toStation, toDepot, toYard */
  toStation: number | null
  toDepot: number | null
  toYard: number | null
  /** станция акта — станция расположения «откуда», id справочника Station */
  station: number
  /** вид работ — id справочника WorkType */
  workType: number
  /** состояние — константы ActStatus */
  actStatusType: number
  /** кто сдал: фамилия и инициалы */
  transferredByUsr: UserRef
  /** когда сдал, ISO с часовым поясом */
  transferredDt: string
  /** кто принял или отказал; null — приём не отмечен */
  receivedByUsr: UserRef | null
  receivedDt: string | null
  rejectReason: string | null
  wagons: { number: string; withProperty: boolean }[]
  /** текущий сотрудник может принять вагоны или отказать */
  canReceive: boolean
}

/** Вагон для акта: строка таблицы вагонов и акт, который ждёт приёма по этому вагону. */
export interface TransferWagon extends WagonRow {
  /** номер акта, который ожидает приёма с этим вагоном; null — вагон можно передать */
  pendingAct: string | null
  /** в акте, который ожидает приёма, вагон передаётся с имуществом; null — такого акта нет */
  pendingWithProperty: boolean | null
}

export interface TransferDraft {
  fromStation: number | null
  fromDepot: number | null
  fromYard: number | null
  toStation: number | null
  toDepot: number | null
  toYard: number | null
  workType: number
  /** кто сдал вагоны — для акта, проведённого сразу */
  transferredByUsr: number | null
  /** время акта: значение DatePicker, время браузера без пояса */
  transferredDt: string
  wagons: { number: string; withProperty: boolean }[]
}

/** Акты, новые первыми. states — константы ActStatus; пусто — все. */
export async function fetchTransfers(states: number[]): Promise<Transfer[]> {
  return (await http.get<Transfer[]>('/transfers', { params: { states } })).data
}

/** Вагоны для акта: те, что стоят в расположении «откуда». */
export async function fetchTransferWagons(from: TransferPlace): Promise<TransferWagon[]> {
  return (await http.get<TransferWagon[]>('/transfers/wagons', { params: from })).data
}

/** Расположение станции и сколько вагонов в нём стоит; заполнено одно из station, depot, yard. */
export interface TransferPlaceWagons extends TransferPlace {
  wagonCount: number
}

/** Сколько вагонов стоит в каждом расположении станции; расположений без вагонов в ответе нет. */
export async function fetchTransferPlaces(station: number): Promise<TransferPlaceWagons[]> {
  return (await http.get<TransferPlaceWagons[]>('/transfers/places', { params: { station } })).data
}

/** Кто может сдать вагоны из расположения «откуда» — для акта, проведённого сразу. */
export async function fetchTransferGivers(from: TransferPlace): Promise<UserRef[]> {
  return (await http.get<UserRef[]>('/transfers/givers', { params: from })).data
}

/** Откуда передаются вагоны акта. */
export const transferFrom = (a: Transfer): TransferPlace => ({
  station: a.fromStation,
  depot: a.fromDepot,
  yard: a.fromYard,
})

/** Куда передаются вагоны акта. */
export const transferTo = (a: Transfer): TransferPlace => ({ station: a.toStation, depot: a.toDepot, yard: a.toYard })

/** Оформить акт. */
export async function createTransfer(draft: TransferDraft): Promise<Transfer> {
  const transferredDt = isoDateTime(new Date(draft.transferredDt))
  return (await http.post<Transfer>('/transfers', { ...draft, transferredDt })).data
}

/** Принять все вагоны акта. */
export async function acceptTransfer(id: number): Promise<Transfer> {
  return (await http.post<Transfer>(`/transfers/${id}/accept`)).data
}

/** Отказать в приёме с причиной. */
export async function rejectTransfer(id: number, reason: string): Promise<Transfer> {
  return (await http.post<Transfer>(`/transfers/${id}/reject`, { reason })).data
}
