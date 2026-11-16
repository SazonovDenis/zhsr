import type { TableSort } from '@/shared/components/table/types'
import type { UserRef } from './requests'
import type { TrainRef } from './trains'
import { LocationType } from '@/shared/constants/systemDicts'
import { http } from './http'

/**
 * API вагонов: список (GET /wagons), варианты выбора (GET /wagons/options), ввод вагона (POST /wagons), журнал.
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
  /** текущий ответственный за вагон; null — не назначен */
  responsibleUsr: UserRef | null
  /** с какого времени ответственный назначен, ISO с часовым поясом */
  dbegResponsible: string
  /** общий пробег, км: пробег на момент ввода плюс пробеги в рейсах */
  runKm: number
  /** изготовитель; null — не указан */
  maker: string | null
  /** год постройки; null — не указан */
  buildYear: number | null
  /** ввод в эксплуатацию, ISO-дата; null — не указан */
  dbeg: string | null
  /** вывод из эксплуатации, ISO-дата; null — вагон в эксплуатации */
  dend: string | null
  /** поезд, в котором вагон сейчас; null — вагон не в поезде */
  train: TrainRef | null
  /** рейсы поезда вагона в состоянии «план» и «выполняется», по плановому отправлению */
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

/**
 * Фильтр запроса списка: при поиске по номеру — только номер и момент at, остальные фильтры не применяются (Ф-ВАГ-04);
 * их отменяет фронт, сервер сочетает номер с остальными фильтрами. Видимость по роли сервер применяет всегда.
 */
export function wagonQuery(filter: WagonFilter): WagonFilter {
  if (!filter.number.trim()) return filter
  return { number: filter.number, station: null, trip: null, locationKinds: [], states: [], at: filter.at ?? null }
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
  /** пробег на момент ввода, км — текст поля, целое неотрицательное */
  initialRunKm: string
}

/**
 * Список вагонов по фильтру. sort — поле и направление сортировки; null — порядок сервера по умолчанию.
 */
export async function fetchWagons(filter: WagonFilter, sort: TableSort | null): Promise<WagonRow[]> {
  return (await http.get<WagonRow[]>('/wagons', { params: { ...filter, sort: sort?.field, desc: sort?.desc } })).data
}

/**
 * Вагон по точному номеру, как строка списка вагонов. Вагона нет — ошибка «Вагон № … не найден»; вагон не виден
 * пользователю по роли — «Вы не имеете доступа к данным по вагону …».
 */
/**
 * Сменить ответственного за вагон без акта (Ф-ВАГ-07): мастер депо станции вагона или администратор. responsibleUsr —
 * сотрудник, null — ответственный не назначен. Ответ — вагон после смены.
 */
export async function changeWagonResponsible(id: number, responsibleUsr: number | null): Promise<WagonRow> {
  return (await http.post<WagonRow>(`/wagons/${id}/responsible`, { responsibleUsr })).data
}

export async function fetchWagonByNumber(number: string): Promise<WagonRow> {
  return (await http.get<WagonRow>(`/wagons/by-number/${encodeURIComponent(number)}`)).data
}

/** Вагоны как варианты выбора: фильтр — как у списка вагонов. */
export async function fetchWagonOptions(filter: WagonFilter): Promise<WagonOption[]> {
  return (await http.get<WagonOption[]>('/wagons/options', { params: filter })).data
}

/** Ввести вагон (Ф-ВАГ-01): год постройки и пробег уходят числами; пустой год — не указан. */
export async function createWagon(draft: WagonDraft): Promise<WagonRow> {
  const body = {
    ...draft,
    buildYear: draft.buildYear.trim() ? Number(draft.buildYear) : null,
    initialRunKm: Number(draft.initialRunKm),
  }
  return (await http.post<WagonRow>('/wagons', body)).data
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

/**
 * Сколько вагонов отдаёт поиск по номеру среди всех вагонов (сервер, ListService.WAGON_SEARCH_LIMIT): пришло ровно
 * столько — номер надо уточнить.
 */
export const WAGON_SEARCH_LIMIT = 50

/**
 * Поиск вагона по цифрам номера среди всех вагонов, без видимости по роли; не больше WAGON_SEARCH_LIMIT вагонов.
 */
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
  /** проверка вагона по чек-листу: расположение и состояние не меняет */
  CHECK: 8,
  /** опись экипировки вагона: расположение и состояние не меняет */
  EQUIP: 9,
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

/**
 * Строка журнала вагона (Ф-ВАГ-05): записи расположения, состояния и ответственного, открытые одним документом в одно
 * время.
 */
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
  /** ответственный после события; null — не назначен */
  responsibleUsr: UserRef | null
  /** ответственный до события; null — не назначен, не менялся или это первая запись */
  prevResponsibleUsr: UserRef | null
  /** событие назначило ответственного */
  responsibleChanged: boolean
  /** основание — константы JournalBasis */
  basisType: number
  /** номер акта приёма-передачи; иначе null */
  actNumber: string | null
  /** рейс отправления, прибытия, отцепки или прицепки; иначе null */
  basisTrip: TripRef | null
  /** кто провёл документ; null — документа нет */
  byUsr: UserRef | null
  /** основания, примечания из документа: прицепки, отцепки, ручной правки; null — нет */
  comment: string | null
  /** проверка вагона — id; есть только у основания CHECK */
  checkList: number | null
  /** шаблон чек-листа — у основания CHECK */
  checkListTemplate: { id: number; textRu: string; textKk: string | null; textEn: string | null } | null
  /** опись — id; есть только у основания EQUIP */
  equipList: number | null
  /** шаблон описи — у основания EQUIP */
  equipListTemplate: { id: number; textRu: string; textKk: string | null; textEn: string | null } | null
  /** число нарушений проверки или описи — у оснований CHECK и EQUIP */
  violationCount: number | null
  /** основание-документ: прицепка или отцепка в рейсе (DETACH, ATTACH) — id train_wagon_change, акт приёма-передачи
   * (TRANSFER) — id акта; иначе null */
  act: number | null
  /** текущий сотрудник может отменить основание: акт (Ф-АКТ-06), прицепку или отцепку (Ф-АКТ-01) */
  canCancel: boolean
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
