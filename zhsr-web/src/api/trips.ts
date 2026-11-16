import type { TableSort } from '@/shared/components/table/types'
import { dayStart, isoDateTime } from '@/shared/format'
import { http } from './http'
import type { UserRef } from './requests'
import type { TrainChange, TrainRef, TrainWagon, WagonBusy } from './trains'
import type { TripRef } from './wagons'
import type { ResponsibleChoice } from '@/shared/wagons/responsible'
import { responsibleUsr } from '@/shared/wagons/responsible'

/**
 * API рейсов: список, создание, правка и отмена, формирование рейса (поезд и бригада), отправление и прибытие,
 * прицепка и отцепка в рейсе. Вагоны поезда меняет api/trains.ts.
 */

/** Пункт готовности рейса к отправлению: состав, бригада и другие. */
export interface TripReadiness {
  text: string
  level: 'ok' | 'warn' | 'bad'
}

/** Строка таблицы рейсов: вариант выбора рейса и поля таблицы. */
export interface TripRow extends TripOption {
  /** вид перевозки — id справочника TransportType */
  transportType: number
  /** фактическое отправление, ISO с часовым поясом */
  departedDt: string | null
  /** фактическое прибытие, ISO с часовым поясом; null — рейс не прибыл */
  arrivedDt: string | null
  /** когда отмечено отправление, ISO; null — не отмечено */
  departureMarkedDt: string | null
  /** когда отмечено прибытие, ISO; null — не отмечено */
  arrivalMarkedDt: string | null
  /** готовность рейса; сервер её пока не считает — пусто */
  readiness: TripReadiness[]
}

export interface TripFilter {
  /** маршрут — id маршрута (api/routes.ts) */
  route: number | null
  /** станция — id справочника Station */
  station: number | null
  /** период по дате планового отправления: ISO-даты «2026-10-30» включительно; null — без ограничения */
  from: string | null
  to: string | null
  /** состояния рейса — id справочника TripState */
  states: number[]
  /** депо: маршрут рейса идёт через станцию депо; null — без ограничения (сервер может ограничить по роли) */
  depot?: number | null
  /** часть номера вагона: вагон в поезде рейса; остальные фильтры не отменяет */
  wagon?: string
}

/** Новый рейс: маршрут, поезд и плановое отправление. Рейс создаётся в состоянии «План». */
export interface TripDraft {
  /** маршрут — id маршрута (api/routes.ts); null — ещё не выбран */
  route: number | null
  /** плановое отправление, ISO; null — ещё не задано */
  plannedDt: string | null
  /** поезд — id поезда (api/trains.ts); null — поезд не назначен */
  train: number | null
}

/**
 * Список рейсов по фильтру. sort — поле и направление сортировки; null — порядок сервера по умолчанию. Даты периода
 * уходят на сервер началом дня в поясе браузера: from — с начала первого дня, to — до начала дня после последнего.
 */
export async function fetchTrips(filter: TripFilter, sort: TableSort | null): Promise<TripRow[]> {
  const params = {
    ...filter,
    from: filter.from ? dayStart(filter.from) : null,
    to: filter.to ? dayStart(filter.to, 1) : null,
    sort: sort?.field,
    desc: sort?.desc,
  }
  return (await http.get<TripRow[]>('/trips', { params })).data
}

/** Рейс как вариант выбора (комбо «Рейс»): рейс и то, что строке варианта нужно от его маршрута. */
export interface TripOption {
  id: number
  /** номер поезда */
  trainNumber: string
  /** маршрут — id маршрута (api/routes.ts) */
  route: number
  /** поезд рейса; null — поезд не назначен */
  train: TrainRef | null
  /** название маршрута: «Алматы — Астана» */
  name: string
  /** вид маршрута — id справочника RouteKind (константы RouteKind) */
  routeKind: number
  /** время в пути, мин */
  duration: number
  /** состояние рейса — id справочника TripState (константы TripStatus) */
  tripStatusType: number
  /** плановое отправление, ISO с часовым поясом */
  plannedDt: string
  /** длина маршрута, км */
  km: number
  /** остановки маршрута по порядку — id справочника Station */
  stops: number[]
}

/**
 * Рейсы как варианты выбора: фильтр — как у списка рейсов; у каждого рейса — вид, длина и остановки его маршрута.
 */
export async function fetchTripOptions(filter: TripFilter): Promise<TripOption[]> {
  const params = {
    ...filter,
    from: filter.from ? dayStart(filter.from) : null,
    to: filter.to ? dayStart(filter.to, 1) : null,
  }
  return (await http.get<TripOption[]>('/trips/options', { params })).data
}

// значение DatePicker — время браузера без пояса, сервер принимает только с поясом
const draftBody = (draft: TripDraft) => ({
  route: draft.route,
  plannedDt: draft.plannedDt ? isoDateTime(new Date(draft.plannedDt)) : null,
  train: draft.train,
})

/** Создать рейс в статусе «План». */
export async function createTrip(draft: TripDraft): Promise<TripRow> {
  return (await http.post<TripRow>('/trips', draftBody(draft))).data
}

/** Исправить маршрут, поезд и плановое отправление рейса в статусе «План». */
export async function updateTrip(tripId: number, draft: TripDraft): Promise<TripRow> {
  return (await http.put<TripRow>(`/trips/${tripId}`, draftBody(draft))).data
}

/** Отменить рейс в статусе «План». */
export async function cancelTrip(tripId: number): Promise<TripRow> {
  return (await http.post<TripRow>(`/trips/${tripId}/cancel`)).data
}

/** Сотрудник бригады рейса. */
export interface CrewMember {
  usr: UserRef
  /** должность — id справочника CrewDuty (константы CrewDuty) */
  crewDutyType: number
  /** категория «старший проводник» — нужна для хвостовых вагонов */
  senior: boolean
  /** вагоны проводника по номеру: один или два соседних; пусто — проводник не распределён */
  wagons: string[]
}

/** Вагон поезда рейса. */
export interface CrewWagon extends TrainWagon {
  /**
   * вагон едет в поезде рейса: в рейсе «план» — на отправлении, иначе — сейчас; иначе отцеплен или прицепляется в
   * пути
   */
  riding: boolean
}

/** Вагон для прицепки или отцепки в рейсе. */
export interface CouplingWagon {
  number: string
  /** тип вагона — id справочника WagonType */
  wagonType: number
  /** вид расположения — id справочника LocationKind */
  locationType: number
  place: number
  /** состояние вагона — id справочника WagonState */
  wagonStatusType: number
  /** занятость вагона в другом поезде; null — вагон свободен */
  busy: WagonBusy | null
  /** можно отцепить или прицепить; иначе показан, но отметить нельзя */
  available: boolean
}

/** Рейс, вагоны поезда рейса по порядку, бригада и прицепки и отцепки рейса. */
export interface TripCrew {
  trip: {
    id: number
    /** маршрут — id маршрута */
    route: number
    /** поезд рейса; null — поезд не назначен */
    train: TrainRef | null
    trainNumber: string
    /** маршрут: «Алматы — Астана» */
    name: string
    /** состояние рейса — id справочника TripState */
    tripStatusType: number
    /** отправление: фактическое, если было, иначе плановое; ISO с часовым поясом */
    departure: string
    /** плановое прибытие, ISO с часовым поясом */
    arrival: string
    /** вид перевозки — id справочника TransportType */
    transportType: number
    /** время в пути, мин */
    duration: number
    /** плановое отправление, ISO с часовым поясом */
    plannedDt: string
    /** фактическое отправление, ISO с часовым поясом; null — рейс не отправлен */
    departedDt: string | null
    /** фактическое прибытие, ISO с часовым поясом; null — рейс не прибыл */
    arrivedDt: string | null
    /** когда отмечено отправление, ISO; null — не отмечено */
    departureMarkedDt: string | null
    /** когда отмечено прибытие, ISO; null — не отмечено */
    arrivalMarkedDt: string | null
    /**
     * после прибытия назначен ответственный за вагоны; false — не назначен, ответственные — проводники вагонов
     */
    responsibleAssigned: boolean
    /** ответственный за вагоны, назначенный после прибытия; null — «не назначен» или назначения нет */
    responsibleUsr: UserRef | null
    /** станция отправления — id справочника Station; null — у маршрута нет остановок */
    departureStation: number | null
    /** станция прибытия — id справочника Station; null — у маршрута нет остановок */
    arrivalStation: number | null
    /** остановки маршрута по порядку — id справочника Station */
    stops: number[]
    /** остановки маршрута — route_station.id, по порядку stops */
    routeStations: number[]
    /** время стоянки на остановках по расписанию рейса, ISO, по порядку stops */
    stopTimes: string[]
  }
  /** вагоны поезда на время рейса: по факту и по плану */
  wagons: CrewWagon[]
  crew: CrewMember[]
  /** прицепки и отцепки рейса: плановые и выполненные */
  changes: TrainChange[]
  /** плановые прицепки и отцепки, которые надо отметить до отправления */
  pending: TrainChange[]
}

/** Проводник для набора в бригаду рейса (диалог «Проводники…»). */
export interface ConductorCandidate extends UserRef {
  senior: boolean
  /** уже в бригаде этого рейса */
  assigned: boolean
  /** рейс, из которого проводник освободится к отправлению; null — свободен */
  busyTrip: TripRef | null
  /** когда этот рейс прибудет */
  busyUntil: string | null
}

/** Сотрудник для должности начальника поезда или ПЭМ; занятый тоже в списке, назначить его не даст сервер. */
export interface StaffCandidate extends UserRef {
  /** рейс, на котором сотрудник занят во время этого рейса; null — свободен */
  busyTrip: TripRef | null
  /** до какого времени занят: фактическое прибытие того рейса, а если его нет — плановое; ISO */
  busyUntil: string | null
}

/** Рейс, его состав и бригада. */
export async function fetchTripCrew(tripId: number): Promise<TripCrew> {
  return (await http.get<TripCrew>(`/trips/${tripId}/crew`)).data
}

/**
 * Закрепить проводника за вагонами: один вагон или два соседних; пустой список — проводник снимается с вагона и
 * становится нераспределённым. Возвращает бригаду после изменения.
 */
export async function setConductorWagons(tripId: number, user: number, wagons: string[]): Promise<TripCrew> {
  return (await http.put<TripCrew>(`/trips/${tripId}/crew/${user}/wagons`, wagons)).data
}

/** Проводники станции отправления и уже назначенные в рейс — для набора проводников рейса. */
export async function fetchConductorCandidates(tripId: number): Promise<ConductorCandidate[]> {
  return (await http.get<ConductorCandidate[]>(`/trips/${tripId}/conductor-candidates`)).data
}

/**
 * Проводники рейса: кто в списке — в бригаде, кого нет — убирается из бригады и с вагона. Новые проводники
 * становятся нераспределёнными.
 */
export async function setTripConductors(tripId: number, users: number[]): Promise<TripCrew> {
  return (await http.put<TripCrew>(`/trips/${tripId}/conductors`, users)).data
}

/** Сотрудники для должности начальника поезда или ПЭМ. duty — CrewDuty.LNP или CrewDuty.PEM. */
export async function fetchStaffCandidates(tripId: number, duty: number): Promise<StaffCandidate[]> {
  return (await http.get<StaffCandidate[]>(`/trips/${tripId}/staff-candidates`, { params: { duty } })).data
}

/** Назначить начальника поезда или ПЭМ; null — снять с должности. */
export async function setTripDuty(tripId: number, duty: number, user: number | null): Promise<TripCrew> {
  return (await http.put<TripCrew>(`/trips/${tripId}/duty/${duty}`, { usr: user })).data
}

/** Отметить отправление рейса. time — ISO с часовым поясом. */
export async function markDeparture(tripId: number, time: string): Promise<TripCrew> {
  return (await http.post<TripCrew>(`/trips/${tripId}/depart`, { time })).data
}

/**
 * Отметить прибытие рейса. time — ISO с часовым поясом. responsible — ответственный за вагоны, выбранный при отметке:
 * id сотрудника или «не назначен»; не выбран (null) — ответственный не меняется, до назначения (assignTripResponsible)
 * им остаётся проводник.
 */
export async function markArrival(
  tripId: number,
  time: string,
  responsible: ResponsibleChoice = null,
): Promise<TripCrew> {
  const body =
    responsible === null ? { time } : { time, assignResponsible: true, responsibleUsr: responsibleUsr(responsible) }
  return (await http.post<TripCrew>(`/trips/${tripId}/arrive`, body)).data
}

/**
 * Назначить ответственного за вагоны прибывшего рейса — на все вагоны поезда на станции прибытия. responsibleUsr —
 * сотрудник, null — ответственный не назначен.
 */
export async function assignTripResponsible(tripId: number, responsibleUsr: number | null): Promise<TripCrew> {
  return (await http.post<TripCrew>(`/trips/${tripId}/responsible`, { responsibleUsr })).data
}

/** Исправить время отправления (10 минут после отметки). time — ISO с часовым поясом. */
export async function retimeDeparture(tripId: number, time: string): Promise<TripCrew> {
  return (await http.put<TripCrew>(`/trips/${tripId}/depart`, { time })).data
}

/** Отменить отправление (10 минут после отметки): рейс снова «план». */
export async function cancelDeparture(tripId: number): Promise<TripCrew> {
  return (await http.delete<TripCrew>(`/trips/${tripId}/depart`)).data
}

/** Исправить время прибытия (10 минут после отметки). time — ISO с часовым поясом. */
export async function retimeArrival(tripId: number, time: string): Promise<TripCrew> {
  return (await http.put<TripCrew>(`/trips/${tripId}/arrive`, { time })).data
}

/** Отменить прибытие (10 минут после отметки): рейс снова «выполняется». */
export async function cancelArrival(tripId: number): Promise<TripCrew> {
  return (await http.delete<TripCrew>(`/trips/${tripId}/arrive`)).data
}

/**
 * Вагоны для прицепки или отцепки в рейсе: для отцепки — едущие в поезде, для прицепки — станции, её депо и парка
 * отстоя. type — константы TrainWagonChangeType.
 */
export async function fetchCouplingWagons(
  tripId: number,
  type: number,
  station: number | null,
): Promise<CouplingWagon[]> {
  const params = { type, station }
  return (await http.get<CouplingWagon[]>(`/trips/${tripId}/coupling-wagons`, { params })).data
}

/** Прицепить или отцепить вагоны в рейсе сразу, без плана. */
export async function coupleWagons(
  tripId: number,
  body: {
    /** константы TrainWagonChangeType */
    changeType: number
    /** промежуточная остановка маршрута — route_station.id */
    routeStation: number
    /** основания, примечания: приказ, причина */
    comment: string
    wagons: string[]
    /** фактическое время, ISO */
    time: string
    /** ответственный за вагоны; null — не назначен */
    responsibleUsr: number | null
  },
): Promise<TripCrew> {
  return (await http.post<TripCrew>(`/trips/${tripId}/coupling`, body)).data
}
