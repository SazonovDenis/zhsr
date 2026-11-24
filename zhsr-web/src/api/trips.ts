import type { TableSort } from '@/shared/components/table/types'
import { TripResponsibleLayer } from '@/shared/constants/TripResponsibleLayer'
import { dayStart, isoDateTime } from '@/shared/format'
import { http } from './http'
import type { UserRef } from './requests'
import type { TrainChange, TrainRef, TrainWagon, WagonBusy } from './trains'
import type { TripRef } from './wagons'
import type { ResponsibleCandidate } from '@/shared/wagons/responsible'

/**
 * API рейсов: список, создание, правка и отмена, формирование рейса (поезд и бригада), отправление и прибытие,
 * прицепка и отцепка в рейсе. Вагоны поезда меняет api/trains.ts.
 */

/** Данные готовности рейса в статусе «План» к отправлению; тексты пунктов строит фронт. */
export interface TripReadiness {
  /** поезд назначен */
  train: boolean
  /** номера вагонов с ожидающими планами, которые надо отметить или отменить до отправления */
  pending: string[]
  /** номера вагонов прицепок и отцепок, выполненных фактом до отправления */
  done: string[]
  /** сколько вагонов едет в поезде на отправлении */
  riding: number
  /** номера едущих вагонов не на станции отправления */
  away: string[]
  /** назначен ЛНП */
  lnp: boolean
  /** назначен ПЭМ */
  pem: boolean
  /** номера пассажирских вагонов, у которых не выполнена норма проводников */
  normNotOk: string[]
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
  /** готовность к отправлению — у рейса в статусе «План»; у остальных null */
  readiness: TripReadiness | null
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
  /**
   * вагоны, за которые сотрудник отвечает на рейс (журнал ответственных: до отправления — план на рейс, в пути — факт),
   * по номеру; пусто — сотрудник не распределён
   */
  wagons: string[]
}

/** Вагон поезда рейса: до отправления — по ожидаемому составу, после — по факту. */
export interface CrewWagon extends TrainWagon {
  /** позиция в поезде: на отправлении (у прицепляемого в пути — при прицепке) */
  seq: number
  /**
   * вагон едет в поезде рейса: в рейсе «план» — на отправлении, «выполняется» — сейчас, «выполнен» — на прибытии;
   * иначе отцеплен или прицепляется в пути
   */
  riding: boolean
  /** прицепки и отцепки вагона в этом рейсе: факты и ожидающие планы, по времени */
  changes: TrainChange[]
}

/** Вагон для прицепки или отцепки в рейсе. */
export interface CouplingWagon {
  /** id вагона */
  wagon: number
  number: string
  /** отцепка: позиция вагона в поезде; прицепка — null */
  seq: number | null
  /** тип вагона — id справочника WagonType */
  wagonType: number
  /** вид расположения — id справочника LocationKind */
  locationType: number | null
  place: number | null
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
  /** вагоны поезда на время рейса: до отправления — ожидаемый состав, после — факт; сначала едущие, по позиции */
  wagons: CrewWagon[]
  crew: CrewMember[]
  /** прицепки и отцепки рейса: факты и ожидающие планы, по времени */
  changes: TrainChange[]
  /** ожидающие планы, которые надо отметить или отменить до отправления: на станции отправления и вне рейса раньше */
  pending: TrainChange[]
  /** отменённые планы рейса, по времени: их можно вернуть */
  cancelled: TrainChange[]
  /** отработанные (подтверждённые) планы рейса, по времени */
  done: TrainChange[]
  /** время последнего факта прицепки или отцепки поезда рейса, ISO: удалить можно только факт этого времени */
  lastFactDt: string | null
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

/** Проводники станции отправления и уже назначенные в рейс — для набора проводников рейса. */
export async function fetchConductorCandidates(tripId: number): Promise<ConductorCandidate[]> {
  return (await http.get<ConductorCandidate[]>(`/trips/${tripId}/conductor-candidates`)).data
}

/**
 * Поездная бригада рейса одной операцией: начальник поезда и ПЭМ (null — не назначен) и проводники. Проводник, которого
 * нет в списке, убирается из бригады и с вагона; новые проводники становятся нераспределёнными.
 */
export async function setTripCrew(
  tripId: number,
  crew: { lnp: number | null; pem: number | null; conductors: number[] },
): Promise<TripCrew> {
  return (await http.put<TripCrew>(`/trips/${tripId}/crew`, crew)).data
}

/** Сотрудники для должности начальника поезда или ПЭМ. duty — CrewDuty.LNP или CrewDuty.PEM. */
export async function fetchStaffCandidates(tripId: number, duty: number): Promise<StaffCandidate[]> {
  return (await http.get<StaffCandidate[]>(`/trips/${tripId}/staff-candidates`, { params: { duty } })).data
}

/** Ответственные одного вагона: usrs — id сотрудников, пусто — ответственный не назначен. */
export interface WagonUsrs {
  /** вагон — wagon.id */
  wagon: number
  usrs: number[]
}

/** Ответственные за вагоны рейса (Ф-РЕЙ-08): вагоны поезда рейса с ответственными слоя layer и кого можно назначить. */
export interface TripResponsibles {
  trip: number
  /** статус рейса — константы TripStatus */
  tripStatusType: number
  /** что показано — константы TripResponsibleLayer */
  layer: number
  /** показан план: изменения записываются планом, без времени изменения */
  plan: boolean
  /** станция, сотрудники депо которой могут быть ответственными; null — только бригада рейса */
  station: number | null
  /** почему пользователь не может менять ответственных; null — может */
  readonlyReason: string | null
  /** вагоны поезда рейса, по позиции в поезде */
  wagons: {
    /** вагон: номер и тип — id справочника WagonType */
    wagon: { id: number; number: string; wagonType: number }
    seq: number
    /** начало показанных ответственных — записи факта или плана, ISO; null — ответственных нет */
    dbeg: string | null
    /** показанные ответственные, по ФИО: факт или план слоя layer */
    usrs: UserRef[]
  }[]
  /** кого можно назначить: бригада рейса, затем сотрудники депо станции; по ФИО */
  candidates: ResponsibleCandidate[]
}

/** Ответственные за вагоны рейса (Ф-РЕЙ-08). layer — константы TripResponsibleLayer. */
export async function fetchTripResponsibles(
  tripId: number,
  layer: number = TripResponsibleLayer.TRIP,
): Promise<TripResponsibles> {
  return (await http.get<TripResponsibles>(`/trips/${tripId}/responsibles`, { params: { layer } })).data
}

/**
 * Сменить ответственных за вагоны рейса (Ф-РЕЙ-08): только изменённые вагоны. layer — TripResponsibleLayer.TRIP,
 * AFTER или FACT. План записывается без времени, факт — одним временем time (ISO с часовым поясом; null — сейчас). У плана
 * после рейса пустой состав снимает план.
 */
export async function saveTripResponsibles(
  tripId: number,
  time: string | null,
  wagons: WagonUsrs[],
  layer: number = TripResponsibleLayer.TRIP,
): Promise<TripResponsibles> {
  return (await http.post<TripResponsibles>(`/trips/${tripId}/responsibles`, { layer, time, wagons })).data
}

/**
 * Отметить отправление рейса. time — ISO с часовым поясом. Отправление отрабатывает план ответственных на рейс;
 * responsibles — правки плана в окне отправления: записываются вместо плана у своих вагонов; null — только план.
 */
export async function markDeparture(
  tripId: number,
  time: string,
  responsibles: WagonUsrs[] | null = null,
): Promise<TripCrew> {
  return (await http.post<TripCrew>(`/trips/${tripId}/depart`, { time, responsibles })).data
}

/**
 * Отметить прибытие рейса. time — ISO с часовым поясом. Прибытие отрабатывает план ответственных после рейса;
 * responsibles — правки в окне прибытия: записываются вместо плана (у вагона без плана — вместо текущих) у своих
 * вагонов; null — только план.
 */
export async function markArrival(
  tripId: number,
  time: string,
  responsibles: WagonUsrs[] | null = null,
): Promise<TripCrew> {
  return (await http.post<TripCrew>(`/trips/${tripId}/arrive`, { time, responsibles })).data
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
    /** вагоны — id */
    wagons: number[]
    /** фактическое время, ISO */
    time: string
    /** прицепка: позиция первого вагона в поезде, с 1; null — в конец */
    seq: number | null
    /** ответственные за вагоны; пусто — не назначены */
    responsibleUsrs: number[]
  },
): Promise<TripCrew> {
  return (await http.post<TripCrew>(`/trips/${tripId}/coupling`, body)).data
}

/** Удалить рейс: на него ссылаются другие записи — ошибка 409 с причиной. */
export async function deleteTrip(id: number): Promise<void> {
  await http.delete(`/trips/${id}`)
}
