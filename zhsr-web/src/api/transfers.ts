import { isoDateTime } from '@/shared/format'
import type { StaffCandidate } from './trips'
import type { UserRef } from './requests'
import { http } from './http'

/** API актов приёма-передачи вагонов (Ф-АКТ-02). */

/** Акт приёма-передачи. */
export interface Transfer {
  id: number
  number: string
  /** направление — константы TransferDirection */
  transferDirectionType: number
  /** станция передачи — id справочника Station */
  station: number
  /** депо — id справочника Depot; null — парк отстоя */
  depot: number | null
  /** парк отстоя — id справочника Yard; null — депо */
  yard: number | null
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

/** Вагон для акта. */
export interface TransferWagon {
  number: string
  /** тип вагона — id справочника WagonType */
  wagonType: number
  /** вид расположения — id справочника LocationKind */
  locationType: number
  place: number
  /** состояние вагона — id справочника WagonState */
  wagonStatusType: number
  /** номер акта, который ожидает приёма с этим вагоном; null — вагон можно передать */
  pendingAct: string | null
}

export interface TransferDraft {
  transferDirectionType: number
  station: number
  depot: number | null
  yard: number | null
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

/** Вагоны для акта: со станции — на станции, из депо или парка отстоя — там. */
export async function fetchTransferWagons(
  direction: number,
  station: number | null,
  depot: number | null,
  yard: number | null,
): Promise<TransferWagon[]> {
  return (await http.get<TransferWagon[]>('/transfers/wagons', { params: { direction, station, depot, yard } })).data
}

/** Кто может сдать вагоны на станции — для акта, проведённого сразу. */
export async function fetchTransferGivers(direction: number, station: number): Promise<StaffCandidate[]> {
  return (await http.get<StaffCandidate[]>('/transfers/givers', { params: { direction, station } })).data
}

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
