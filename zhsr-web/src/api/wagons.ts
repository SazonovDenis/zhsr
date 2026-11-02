import type { TableSort } from '@/shared/components/table/types'
import { fakeSort } from './fakeSort'
import { fakeNetwork } from './http'
import { FAKE_COMPOSITION } from './trips'
// import { http } from './http'

/**
 * API вагонов. Сервер пока не готов: функции отдают фиктивные данные, а над ними закомментирован будущий вызов
 * REST.
 */

/** Расположение вагона: где он сейчас и с какого времени. */
export interface WagonLocation {
  /** вид расположения — id справочника LocationKind */
  kind: string
  /** название места: станция, депо, рейс, парк отстоя */
  name: string
  /** с какого времени, ISO */
  since: string
}

export interface Wagon {
  id: string
  number: string
  /** тип вагона — id справочника WagonType */
  type: string
  /** модель — id справочника WagonModel */
  model: string
  /** депо приписки — id справочника Depot */
  depot: string
  location: WagonLocation
  /** состояние вагона — id справочника WagonState */
  state: string
  /** с какого времени вагон в этом состоянии, ISO */
  stateSince: string
}

export interface WagonFilter {
  /** поиск по номеру вагона */
  number: string
  /** станция — id справочника Station */
  station: string | null
  /** рейс — id рейса (api/trips.ts) */
  trip: string | null
  /** виды расположения — id справочника LocationKind */
  locationKinds: string[]
  /** состояния вагона — id справочника WagonState */
  states: string[]
}

/** Новый вагон: паспорт, депо приписки, начальное расположение и состояние. */
export interface WagonDraft {
  number: string
  /** тип вагона — id справочника WagonType */
  type: string
  /** модель — id справочника WagonModel */
  /** модель — id справочника WagonModel */
  model: string
  year: string
  maker: string
  /** депо приписки — id справочника Depot */
  depot: string
  /** вид начального расположения — id справочника LocationKind: DEPOT, YARD или STATION */
  locationKind: string
  /** место: id справочника Depot, Yard или Station — по виду расположения */
  locationRef: string
  /** начальное состояние — id справочника WagonState */
  state: string
  /** ввод в эксплуатацию, ISO; null — ещё не задан */
  since: string | null
}

/**
 * Список вагонов по фильтру. sort — поле и направление сортировки; null — порядок сервера по умолчанию.
 */
export async function fetchWagons(filter: WagonFilter, sort: TableSort | null): Promise<Wagon[]> {
  // return (await http.get<Wagon[]>('/wagons', { params: { ...filter, sort: sort?.field, desc: sort?.desc } })).data
  return Promise.resolve(
    fakeSort(
      FAKE_WAGONS.filter(
        (w) =>
          w.number.includes(filter.number) &&
          (filter.locationKinds.length === 0 || filter.locationKinds.includes(w.location.kind)) &&
          (filter.states.length === 0 || filter.states.includes(w.state)),
      ),
      sort,
    ),
  )
}

export async function createWagon(draft: WagonDraft): Promise<Wagon> {
  // return (await http.post<Wagon>('/wagons', draft)).data
  const wagon: Wagon = {
    id: draft.number,
    number: draft.number,
    type: draft.type,
    model: draft.model,
    depot: draft.depot,
    location: { kind: draft.locationKind, name: draft.locationRef, since: draft.since ?? '' },
    state: draft.state,
    stateSince: draft.since ?? '',
  }
  FAKE_WAGONS.push(wagon)
  return Promise.resolve(wagon)
}

/** Рейс в данных: id — для запросов, name — для показа. */
export interface TripRef {
  id: string
  /** номер поезда и маршрут: «002 Алматы — Астана» */
  name: string
}

/** Вагон для выбора в заявке на ремонт: номер, тип вагона, рейс и где вагон сейчас. */
export interface RequestWagon {
  number: string
  /** тип вагона — id справочника WagonType */
  type: string
  /** рейс, в составе которого вагон едет сейчас; null — вагон не в рейсе */
  trip: TripRef | null
  /** где вагон сейчас: рейс, депо, станция или парк отстоя — для показа */
  place: string
  /** станция расположения вагона — id справочника Station; null — вагон в рейсе */
  station: string | null
}

/** Вагоны состава рейса по порядку — фильтр по умолчанию при подаче заявки. */
export async function fetchTripWagons(tripId: string): Promise<RequestWagon[]> {
  // return (await http.get<RequestWagon[]>(`/trips/${tripId}/wagons`)).data
  fakeNetwork()
  const numbers = FAKE_COMPOSITION[tripId] ?? []
  return Promise.resolve(numbers.map((n) => fakeRequestWagon(n)).filter((w): w is RequestWagon => !!w))
}

/** Поиск вагона по цифрам номера среди всех вагонов — когда фильтр по рейсу снят. */
export async function findWagons(number: string): Promise<RequestWagon[]> {
  // return (await http.get<RequestWagon[]>('/wagons/find', { params: { number } })).data
  fakeNetwork()
  return Promise.resolve(FAKE_WAGONS.filter((w) => w.number.includes(number)).map((w) => fakeRequestWagon(w.number)!))
}

/** Фиктивный вагон для заявки по номеру; нужен и заготовке API заявок. */
export function fakeRequestWagon(number: string): RequestWagon | null {
  const w = FAKE_WAGONS.find((x) => x.number === number)
  if (!w) return null
  const trip = Object.keys(FAKE_COMPOSITION).find((id) => FAKE_COMPOSITION[id]!.includes(number))
  return {
    number: w.number,
    type: w.type,
    trip: trip ? { id: trip, name: FAKE_TRIP_NAMES[trip] ?? trip } : null,
    place: w.location.name,
    station: FAKE_STATIONS[w.number] ?? null,
  }
}

// --- фиктивные данные: значения из макета ---

const FAKE_TRIP_NAMES: Record<string, string> = { T2: '002 Алматы — Астана' }

/** Станция расположения вагонов не в рейсе. */
const FAKE_STATIONS: Record<string, string> = {
  '15021': 'AST',
  '15023': 'ALA',
  '15024': 'KRG',
  '15025': 'ALA',
  '16540': 'AST',
  '27140': 'AST',
}

const inTrip = (number: string, type: string): Wagon => ({
  id: number,
  number,
  type,
  model: 'TULPAR_TMH',
  depot: 'D2',
  location: { kind: 'TRIP', name: 'Рейс 002 Алматы — Астана', since: '2026-10-28T21:05' },
  state: 'IN_TRIP',
  stateSince: '2026-10-28T21:05',
})

const FAKE_WAGONS: Wagon[] = [
  {
    id: '15021',
    number: '15021',
    type: 'COMPARTMENT',
    model: 'TULPAR_TALGO',
    depot: 'D1',
    location: { kind: 'DEPOT', name: 'Вагонное депо Астана', since: '2026-10-27T09:10' },
    state: 'IN_IDLE',
    stateSince: '2026-10-27T09:10',
  },
  {
    id: '15022',
    number: '15022',
    type: 'PLATZKART',
    model: 'TULPAR_TMH',
    depot: 'D1',
    location: { kind: 'TRIP', name: 'Рейс 001 Астана — Алматы', since: '2026-10-28T21:00' },
    state: 'IN_TRIP',
    stateSince: '2026-10-28T21:00',
  },
  {
    id: '15023',
    number: '15023',
    type: 'SLEEPER',
    model: 'STADLER_SLEEPER',
    depot: 'D2',
    location: { kind: 'DEPOT', name: 'Вагонное депо Алматы', since: '2026-10-20T08:00' },
    state: 'IN_REPAIR',
    stateSince: '2026-10-20T08:00',
  },
  {
    id: '15024',
    number: '15024',
    type: 'RESTAURANT',
    model: 'TULPAR_TALGO',
    depot: 'D4',
    location: { kind: 'YARD', name: 'Караганды-Сортировочная', since: '2026-10-15T12:30' },
    state: 'RESERVE',
    stateSince: '2026-10-15T12:30',
  },
  {
    id: '15025',
    number: '15025',
    type: 'STAFF',
    model: 'STADLER_STAFF',
    depot: 'D2',
    location: { kind: 'STATION', name: 'Алматы-2', since: '2026-09-30T10:00' },
    state: 'OUT_OF_SERVICE',
    stateSince: '2026-09-30T10:00',
  },
  {
    id: '16540',
    number: '16540',
    type: 'SLEEPER',
    model: 'STADLER_SLEEPER',
    depot: 'D1',
    location: { kind: 'DEPOT', name: 'Вагонное депо Астана', since: '2026-10-29T14:00' },
    state: 'IN_REPAIR',
    stateSince: '2026-10-29T14:00',
  },
  {
    id: '27140',
    number: '27140',
    type: 'COMPARTMENT',
    model: 'TULPAR_TMH',
    depot: 'D1',
    location: { kind: 'STATION', name: 'Астана-1', since: '2026-10-30T07:40' },
    state: 'IN_IDLE',
    stateSince: '2026-10-30T07:40',
  },
  // состав рейса 002 Алматы — Астана
  inTrip('17043', 'PLATZKART'),
  inTrip('17431', 'PLATZKART'),
  inTrip('27109', 'COMPARTMENT'),
  inTrip('27133', 'COMPARTMENT'),
  inTrip('16532', 'SLEEPER'),
  inTrip('15030', 'RESTAURANT'),
  inTrip('27000', 'STAFF'),
]
