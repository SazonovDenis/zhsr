import type { TableSort } from '@/shared/components/table/types'
import type { UserRef } from './requests'
import { LocationType } from '@/shared/constants/systemDicts'
import { http } from './http'

/**
 * API вагонов. Список (GET /wagons) и варианты выбора (GET /wagons/options) — с сервера; ввод вагона сервер пока не
 * умеет: createWagon — заготовка.
 */

/** Вагон как вариант выбора (подбор вагона в заявке): вагон, тип и где он сейчас. */
export interface WagonOption {
  id: number
  number: string
  /** тип вагона — id справочника WagonType */
  wagonType: number
  /** вид расположения — id справочника LocationKind (константы LocationType) */
  locationType: number
  /** место расположения по виду: id справочника Station, Depot, Yard или id рейса */
  place: number
  /** рейс, в составе которого вагон; null — вагон не в рейсе */
  trip: TripRef | null
}

/** Строка таблицы вагонов: вариант выбора вагона и поля таблицы. */
export interface WagonRow extends WagonOption {
  /** модель — id справочника WagonModel; null — не задана */
  modelType: number | null
  /** депо приписки — id справочника Depot; null — не задано */
  depot: number | null
  /** с какого времени вагон в этом расположении, ISO с часовым поясом */
  dbegLocation: string
  /** состояние вагона — id справочника WagonState (константы WagonStatus) */
  wagonStatusType: number
  /** с какого времени вагон в этом состоянии, ISO с часовым поясом */
  dbegStatus: string
  /** изготовитель; null — не указан */
  maker: string | null
  /** год постройки; null — не указан */
  buildYear: number | null
  /** ввод в эксплуатацию, ISO-дата; null — не указан */
  dbeg: string | null
  /** вывод из эксплуатации, ISO-дата; null — вагон в эксплуатации */
  dend: string | null
  /** рейсы в состоянии «план» и «выполняется», в составе которых вагон, по плановому отправлению */
  trips: WagonTrip[]
  /** акт, ожидающий приёма по вагону; null — нет */
  pendingTransfer: WagonPendingTransfer | null
  /** расположение на момент фильтра at — по журналу; null — момент не задан или вагона тогда ещё не было */
  locationAt?: WagonPlace | null
  /** состояние на момент фильтра at — id справочника WagonState; null — как у locationAt */
  wagonStatusTypeAt?: number | null
}

/** Рейс, в составе которого вагон: «план» или «выполняется». */
export interface WagonTrip {
  trip: TripRef
  /** состояние рейса — id справочника TripState */
  tripStatusType: number
}

/** Акт приёма-передачи, ожидающий приёма по вагону: номер и куда передаётся вагон (ровно одно из to*). */
export interface WagonPendingTransfer {
  id: number
  number: string
  toStation: number | null
  toDepot: number | null
  toYard: number | null
}

export interface WagonFilter {
  /** поиск по номеру вагона */
  number: string
  /** станция — id справочника Station */
  station: number | null
  /** рейс — id рейса (api/trips.ts) */
  trip: number | null
  /** виды расположения — id справочника LocationKind */
  locationKinds: number[]
  /** состояния вагона — id справочника WagonState */
  states: number[]
  /** депо приписки — id справочника Depot; null — без ограничения (сервер может ограничить по роли) */
  depot?: number | null
  /** момент, например отправление рейса, ISO с поясом: дополнительно расположение и состояние на него */
  at?: string | null
}

/** Новый вагон: паспорт (wagon), депо приписки, начальное расположение и состояние. */
export interface WagonDraft {
  number: string
  /** тип вагона — id справочника WagonType */
  wagonType: number | null
  /** модель — id справочника WagonModel */
  modelType: number | null
  buildYear: string
  maker: string
  /** депо приписки — id справочника Depot */
  depot: number | null
  /** вид начального расположения — id справочника LocationKind: депо, парк отстоя или станция */
  locationType: number
  /** место: id справочника Depot, Yard или Station — по виду расположения */
  place: number | null
  /** начальное состояние — id справочника WagonState */
  wagonStatusType: number | null
  /** ввод в эксплуатацию, ISO-дата; null — ещё не задан */
  dbeg: string | null
}

/**
 * Список вагонов по фильтру. sort — поле и направление сортировки; null — порядок сервера по умолчанию.
 */
export async function fetchWagons(filter: WagonFilter, sort: TableSort | null): Promise<WagonRow[]> {
  return (await http.get<WagonRow[]>('/wagons', { params: { ...filter, sort: sort?.field, desc: sort?.desc } })).data
}

/** Вагоны как варианты выбора: фильтр — как у списка вагонов. */
export async function fetchWagonOptions(filter: WagonFilter): Promise<WagonOption[]> {
  return (await http.get<WagonOption[]>('/wagons/options', { params: filter })).data
}

/** Ввести вагон. Сервер пока не умеет: ошибка с текстом для пользователя. */
export function createWagon(draft: WagonDraft): Promise<WagonRow> {
  // return (await http.post<WagonRow>('/wagons', draft)).data
  void draft
  return Promise.reject(new Error('Ввод вагона пока не поддерживается сервером'))
}

/** Рейс в данных. */
export interface TripRef {
  id: number
  /** номер поезда маршрута */
  trainNumber: string
  /** название маршрута */
  name: string
  /** плановое отправление */
  plannedDt: string
}

/** Вагон для выбора в заявке на ремонт: вагон, тип, рейс и где вагон сейчас. */
export interface RequestWagon {
  /** вагон — id вагона */
  id: number
  number: string
  /** тип вагона — id справочника WagonType */
  wagonType: number
  /** рейс, в составе которого вагон едет сейчас; null — вагон не в рейсе */
  trip: TripRef | null
  /** вид расположения — id справочника LocationKind */
  locationType: number
  /** место расположения — id справочника по виду расположения (Station, Depot, Yard); в рейсе — null */
  place: number | null
  /** станция расположения вагона — id справочника Station; null — вагон не на станции */
  station: number | null
}

/**
 * Вагоны состава рейса — фильтр по умолчанию при подаче заявки. Из вариантов выбора (GET /wagons/options?trip=): порядок
 * в составе сервер ещё не отдаёт — по номеру вагона.
 */
export async function fetchTripWagons(tripId: number): Promise<RequestWagon[]> {
  const wagons = await fetchWagonOptions({ number: '', station: null, trip: tripId, locationKinds: [], states: [] })
  return wagons.map(requestWagon)
}

/** Поиск вагона по цифрам номера среди всех вагонов — когда фильтр по рейсу снят. */
export async function findWagons(number: string): Promise<RequestWagon[]> {
  const wagons = await fetchWagonOptions({ number, station: null, trip: null, locationKinds: [], states: [] })
  return wagons.map(requestWagon)
}

/** Вариант выбора вагона — вагон для заявки. */
export function requestWagon(w: WagonOption): RequestWagon {
  const inTrip = w.locationType === LocationType.TRIP
  return {
    id: w.id,
    number: w.number,
    wagonType: w.wagonType,
    trip: w.trip,
    locationType: w.locationType,
    place: inTrip ? null : w.place,
    station: w.locationType === LocationType.STATION ? w.place : null,
  }
}

/** Основание записи журнала вагона — как на сервере (WagonJournalRow.BASIS_*). */
export const JournalBasis = {
  /** ввод вагона: первая запись журнала без документа */
  ENTRY: 1,
  /** ручная правка: запись без документа, не первая */
  MANUAL: 2,
  TRANSFER: 3,
  DETACH: 4,
  ATTACH: 5,
  DEPARTURE: 6,
  ARRIVAL: 7,
} as const

/** Расположение вагона в записи журнала. */
export interface WagonPlace {
  /** вид расположения — id справочника LocationKind */
  locationType: number
  /** место по виду: id справочника Station, Depot, Yard или id рейса */
  place: number
  /** рейс, в составе которого вагон; null — вагон не в рейсе */
  trip: TripRef | null
}

/** Строка журнала вагона (Ф-ВАГ-05): записи расположения и состояния, открытые одним документом в одно время. */
export interface WagonJournalRow {
  /** время события, ISO с часовым поясом */
  dbeg: string
  /** расположение после события */
  location: WagonPlace | null
  /** расположение до события; null — не менялось или это первая запись */
  prevLocation: WagonPlace | null
  /** событие сменило расположение */
  locationChanged: boolean
  /** состояние после события — id справочника WagonState */
  wagonStatusType: number
  /** состояние до события; null — не менялось или это первая запись */
  prevWagonStatusType: number | null
  /** событие сменило состояние */
  statusChanged: boolean
  /** основание — константы JournalBasis */
  basisType: number
  /** номер акта приёма-передачи, отцепки или прицепки; иначе null */
  actNumber: string | null
  /** рейс отправления, прибытия, отцепки или прицепки; иначе null */
  basisTrip: TripRef | null
  /** кто провёл документ; null — документа нет */
  byUsr: UserRef | null
  /** причина из акта отцепки или прицепки; null — нет */
  comment: string | null
}

/**
 * Журнал вагона за период: from — начало, to — конец, не включая (ISO с поясом); null — без границы. Новые первыми.
 */
export async function fetchWagonJournal(
  wagonId: number,
  from: string | null,
  to: string | null,
): Promise<WagonJournalRow[]> {
  return (await http.get<WagonJournalRow[]>(`/wagons/${wagonId}/journal`, { params: { from, to } })).data
}
