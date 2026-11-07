import type { TableSort } from '@/shared/components/table/types'
import { dayStart, isoDateTime } from '@/shared/format'
import { http } from './http'
import type { UserRef } from './requests'
import type { TripRef } from './wagons'

/**
 * API рейсов: список, создание, правка и отмена, формирование рейса (состав и бригада), отправление и прибытие,
 * отцепка и прицепка.
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
}

/** Новый рейс: маршрут и плановое отправление. Рейс создаётся в состоянии «План». */
export interface TripDraft {
  /** маршрут — id маршрута (api/routes.ts); null — ещё не выбран */
  route: number | null
  /** плановое отправление, ISO; null — ещё не задано */
  plannedDt: string | null
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
})

/** Создать рейс в статусе «План». */
export async function createTrip(draft: TripDraft): Promise<TripRow> {
  return (await http.post<TripRow>('/trips', draftBody(draft))).data
}

/** Исправить маршрут и плановое отправление рейса в статусе «План». */
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

/** Вагон состава в бригаде рейса. */
export interface CrewWagon {
  number: string
  /** тип вагона — id справочника WagonType */
  wagonType: number
  /** пассажирский вагон: норма проводников требуется только для пассажирских вагонов */
  passenger: boolean
  /** вид расположения — id справочника LocationKind (константы LocationType) */
  locationType: number
  /** место расположения по виду: станция, депо, парк отстоя или рейс */
  place: number
  /** рейс, в составе которого вагон сейчас; null — вагон не в рейсе */
  trip: TripRef | null
  /** вагон отцеплен актом и в составе не едет */
  detached: boolean
}

/** Акт отцепки или прицепки по рейсу. */
export interface CouplingAct {
  id: number
  number: string
  /** вид акта — константы CouplingType */
  couplingType: number
  /** станция остановки — id справочника Station */
  station: number
  /** номера вагонов */
  wagons: string[]
  /** когда составлен, ISO с часовым поясом */
  actDt: string
  /** состояние акта — константы ActStatus */
  actStatusType: number
}

/** Вагон в акте отцепки или прицепки. */
export interface CouplingWagon {
  number: string
  /** тип вагона — id справочника WagonType */
  wagonType: number
  /** вид расположения — id справочника LocationKind */
  locationType: number
  place: number
  /** состояние вагона — id справочника WagonState */
  wagonStatusType: number
  /** другой активный рейс, в составе которого вагон; null — такого рейса нет */
  otherTrip: TripRef | null
  /** можно отцепить или прицепить; иначе показан, но отметить нельзя */
  available: boolean
}

/** Бригада рейса: рейс, состав по порядку и сотрудники бригады. */
export interface TripCrew {
  trip: {
    id: number
    /** маршрут — id маршрута */
    route: number
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
    /** станция отправления — id справочника Station; null — у маршрута нет остановок */
    departureStation: number | null
    /** станция прибытия — id справочника Station; null — у маршрута нет остановок */
    arrivalStation: number | null
    /** остановки маршрута по порядку — id справочника Station */
    stops: number[]
  }
  wagons: CrewWagon[]
  crew: CrewMember[]
  /** акты отцепки и прицепки, новые первыми */
  acts: CouplingAct[]
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

/** Вагон для добавления в состав: на станции отправления, в депо или в парке отстоя при ней. */
export interface WagonCandidate {
  number: string
  /** тип вагона — id справочника WagonType */
  wagonType: number
  /** вид расположения — id справочника LocationKind */
  locationType: number
  place: number
  /** состояние вагона — id справочника WagonState */
  wagonStatusType: number
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

/** Свободные вагоны для добавления в состав. */
export async function fetchWagonCandidates(tripId: number): Promise<WagonCandidate[]> {
  return (await http.get<WagonCandidate[]>(`/trips/${tripId}/wagon-candidates`)).data
}

/** Состав рейса целиком, по порядку: вагон не из списка уходит из состава и снимается с проводников. */
export async function setTripWagons(tripId: number, wagons: string[]): Promise<TripCrew> {
  return (await http.put<TripCrew>(`/trips/${tripId}/wagons`, wagons)).data
}

/** Отметить отправление рейса. time — ISO с часовым поясом. */
export async function markDeparture(tripId: number, time: string): Promise<TripCrew> {
  return (await http.post<TripCrew>(`/trips/${tripId}/depart`, { time })).data
}

/** Отметить прибытие рейса. time — ISO с часовым поясом. */
export async function markArrival(tripId: number, time: string): Promise<TripCrew> {
  return (await http.post<TripCrew>(`/trips/${tripId}/arrive`, { time })).data
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

/** Вагоны для акта: для отцепки — едущие в составе, для прицепки — станции, её депо и парка отстоя. */
export async function fetchCouplingWagons(
  tripId: number,
  type: number,
  station: number | null,
): Promise<CouplingWagon[]> {
  const params = { type, station }
  return (await http.get<CouplingWagon[]>(`/trips/${tripId}/coupling-wagons`, { params })).data
}

/** Провести акт отцепки или прицепки. */
export async function coupleWagons(
  tripId: number,
  act: { couplingType: number; station: number; orderInfo: string; reason: string; wagons: string[]; actDt: string },
): Promise<TripCrew> {
  return (await http.post<TripCrew>(`/trips/${tripId}/coupling`, act)).data
}
