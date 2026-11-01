import type { TableSort } from '@/shared/components/table/types'
import { fakeSort } from './fakeSort'
// import { http } from './http'

/**
 * API рейсов. Сервер пока не готов: функции отдают фиктивные данные, а над ними закомментирован будущий вызов REST.
 */

/** Пункт готовности рейса к отправлению: состав, бригада и другие. */
export interface TripReadiness {
  text: string
  level: 'ok' | 'warn' | 'bad'
}

export interface Trip {
  id: string
  /** номер поезда */
  train: string
  /** маршрут — id маршрута (api/routes.ts) */
  route: string
  routeName: string
  /** вид перевозки на русском; переводится при показе */
  routeKind: string
  /** время в пути, часов */
  durationHours: number
  /** состояние рейса — id справочника TripState */
  state: string
  /** плановое отправление, ISO */
  planned: string
  /** фактическое отправление, ISO */
  fact: string | null
  readiness: TripReadiness[]
}

export interface TripFilter {
  /** маршрут — id маршрута (api/routes.ts) */
  route: string | null
  /** станция — id справочника Station */
  station: string | null
  /** период по дате планового отправления: ISO-даты «2026-10-30» включительно; null — без ограничения */
  from: string | null
  to: string | null
  /** состояния рейса — id справочника TripState */
  states: string[]
}

/** Новый рейс: маршрут и плановое отправление. Рейс создаётся в состоянии «План». */
export interface TripDraft {
  /** маршрут — id маршрута (api/routes.ts); null — ещё не выбран */
  route: string | null
  /** плановое отправление, ISO; null — ещё не задано */
  planned: string | null
}

/**
 * Список рейсов по фильтру. sort — поле и направление сортировки; null — порядок сервера по умолчанию.
 */
export async function fetchTrips(filter: TripFilter, sort: TableSort | null): Promise<Trip[]> {
  // return (await http.get<Trip[]>('/trips', { params: { ...filter, sort: sort?.field, desc: sort?.desc } })).data
  return Promise.resolve(
    fakeSort(
      FAKE_TRIPS.filter(
        (trip) =>
          (filter.states.length === 0 || filter.states.includes(trip.state)) &&
          (!filter.from || trip.planned.slice(0, 10) >= filter.from) &&
          (!filter.to || trip.planned.slice(0, 10) <= filter.to),
      ),
      sort,
    ),
  )
}

export async function createTrip(draft: TripDraft): Promise<Trip> {
  // return (await http.post<Trip>('/trips', draft)).data
  const trip: Trip = {
    id: `T${FAKE_TRIPS.length + 100}`,
    train: '—',
    route: draft.route ?? '',
    routeName: draft.route ?? '',
    routeKind: 'Пассажирский',
    durationHours: 0,
    state: 'PLANNED',
    planned: draft.planned ?? '',
    fact: null,
    readiness: [],
  }
  FAKE_TRIPS.push(trip)
  return Promise.resolve(trip)
}

// --- фиктивные данные: значения из макета ---

const FAKE_TRIPS: Trip[] = [
  {
    id: 'T1',
    route: 'R1',
    train: '001',
    routeName: 'Астана — Алматы',
    routeKind: 'Пассажирский',
    durationHours: 19,
    state: 'PLANNED',
    planned: '2026-10-30T21:00',
    fact: null,
    readiness: [
      { text: 'Состав', level: 'ok' },
      { text: 'Бригада', level: 'warn' },
    ],
  },
  {
    id: 'T2',
    route: 'R2',
    train: '002',
    routeName: 'Алматы — Астана',
    routeKind: 'Пассажирский',
    durationHours: 19,
    state: 'IN_PROGRESS',
    planned: '2026-10-28T21:00',
    fact: '2026-10-28T21:05',
    readiness: [
      { text: 'Состав', level: 'ok' },
      { text: 'Бригада', level: 'ok' },
    ],
  },
  {
    id: 'T7',
    route: 'R6',
    train: '015',
    routeName: 'Астана — Петропавловск',
    routeKind: 'Пассажирский',
    durationHours: 9,
    state: 'PLANNED',
    planned: '2026-10-28T21:00',
    fact: null,
    readiness: [
      { text: 'Состав', level: 'bad' },
      { text: 'Бригада', level: 'warn' },
    ],
  },
]
