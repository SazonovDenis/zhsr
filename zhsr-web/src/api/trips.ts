import type { TableSort } from '@/shared/components/table/types'
import { fakeSort } from './fakeSort'
import { fakeTime } from './fakeTime'
import { fakeNetwork } from './http'
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

/** Должность в бригаде рейса — id справочника CrewDuty. */
export type CrewDuty = 'LNP' | 'PEM' | 'CONDUCTOR'

/** Сотрудник бригады рейса. */
export interface CrewMember {
  /** id сотрудника */
  user: string
  /** фамилия и инициалы */
  name: string
  duty: CrewDuty
  /** категория «старший проводник» — нужна для хвостовых вагонов */
  senior: boolean
  /** вагоны проводника по номеру: один или два соседних; пусто — проводник не распределён */
  wagons: string[]
}

/** Вагон состава в бригаде рейса. */
export interface CrewWagon {
  number: string
  /** тип вагона — id справочника WagonType */
  type: string
  /** пассажирский вагон: норма проводников требуется только для пассажирских вагонов */
  passenger: boolean
}

/** Бригада рейса: рейс, состав по порядку и сотрудники бригады. */
export interface TripCrew {
  trip: {
    id: string
    train: string
    /** маршрут: «Алматы — Астана» */
    name: string
    /** состояние рейса — id справочника TripState */
    state: string
    /** отправление: фактическое, если было, иначе плановое; ISO */
    departure: string
    /** плановое прибытие, ISO */
    arrival: string
    /** станция отправления — id справочника Station */
    departureStation: string
  }
  wagons: CrewWagon[]
  crew: CrewMember[]
}

/** Проводник для набора в бригаду рейса (диалог «Проводники…»). */
export interface ConductorCandidate {
  user: string
  name: string
  senior: boolean
  /** уже в бригаде этого рейса */
  assigned: boolean
  /** занят в другом рейсе: «в рейсе 001 Астана — Алматы • свободен с 30.10 21:00»; null — свободен */
  busy: string | null
}

/** Бригада рейса с составом. */
export async function fetchTripCrew(tripId: string): Promise<TripCrew> {
  // return (await http.get<TripCrew>(`/trips/${tripId}/crew`)).data
  fakeNetwork()
  return Promise.resolve(fakeTripCrew(tripId))
}

/**
 * Закрепить проводника за вагонами: один вагон или два соседних; пустой список — проводник снимается с вагона и
 * становится нераспределённым. Возвращает бригаду после изменения.
 */
export async function setConductorWagons(tripId: string, user: string, wagons: string[]): Promise<TripCrew> {
  // return (await http.put<TripCrew>(`/trips/${tripId}/crew/${user}/wagons`, wagons)).data
  fakeNetwork()
  const member = FAKE_CREW[tripId]?.find((c) => c.user === user)
  if (member) member.wagons = [...wagons]
  return Promise.resolve(fakeTripCrew(tripId))
}

/** Проводники станции отправления и уже назначенные в рейс — для набора проводников рейса. */
export async function fetchConductorCandidates(tripId: string): Promise<ConductorCandidate[]> {
  // return (await http.get<ConductorCandidate[]>(`/trips/${tripId}/conductor-candidates`)).data
  fakeNetwork()
  const crew = FAKE_CREW[tripId] ?? []
  return Promise.resolve(
    FAKE_CONDUCTORS.map((c) => ({
      user: c.user,
      name: c.name,
      senior: c.senior,
      assigned: crew.some((m) => m.user === c.user && m.duty === 'CONDUCTOR'),
      busy: c.busy,
    })),
  )
}

/**
 * Проводники рейса: кто в списке — в бригаде, кого нет — убирается из бригады и с вагона. Новые проводники
 * становятся нераспределёнными.
 */
export async function setTripConductors(tripId: string, users: string[]): Promise<void> {
  // await http.put(`/trips/${tripId}/conductors`, users)
  fakeNetwork()
  const crew = (FAKE_CREW[tripId] ??= [])
  FAKE_CREW[tripId] = crew.filter((m) => m.duty !== 'CONDUCTOR' || users.includes(m.user))
  for (const user of users) {
    if (FAKE_CREW[tripId].some((m) => m.user === user)) continue
    const c = FAKE_CONDUCTORS.find((x) => x.user === user)
    if (c) FAKE_CREW[tripId].push({ user, name: c.name, duty: 'CONDUCTOR', senior: c.senior, wagons: [] })
  }
  return Promise.resolve()
}

// --- фиктивные данные: значения из макета ---

/** Состав рейсов по порядку: номера вагонов. */
export const FAKE_COMPOSITION: Record<string, string[]> = {
  T2: ['17043', '17431', '27109', '27133', '16532', '15030', '27000'],
}

const FAKE_WAGON_TYPES: Record<string, string> = {
  '17043': 'PLATZKART',
  '17431': 'PLATZKART',
  '27109': 'COMPARTMENT',
  '27133': 'COMPARTMENT',
  '16532': 'SLEEPER',
  '15030': 'RESTAURANT',
  '27000': 'STAFF',
}

const PASSENGER_TYPES = ['PLATZKART', 'COMPARTMENT', 'SLEEPER', 'LUX', 'SEATING']

const conductor = (user: string, name: string, senior: boolean, wagons: string[] = []): CrewMember => ({
  user,
  name,
  duty: 'CONDUCTOR',
  senior,
  wagons,
})

const FAKE_CREW: Record<string, CrewMember[]> = {
  T2: [
    { user: 'u3', name: 'Ахметов Б. К.', duty: 'LNP', senior: false, wagons: [] },
    { user: 'u7', name: 'Касымов Д. Е.', duty: 'PEM', senior: false, wagons: [] },
    conductor('u16', 'Абенова Д. С.', true, ['17043']),
    conductor('u17', 'Серикова А. М.', true, ['17043']),
    conductor('u18', 'Жумабаев Н. К.', false, ['17431', '27109']),
    conductor('u19', 'Ким О. В.', false, ['17431']),
    conductor('u20', 'Тулеуова Д. А.', true, ['27109']),
    conductor('u21', 'Омаров Е. Б.', true, ['27133']),
    conductor('u22', 'Бекова С. Н.', false),
  ],
}

/** Проводники станции отправления рейса T2 (Алматы-2). */
const FAKE_CONDUCTORS: { user: string; name: string; senior: boolean; busy: string | null }[] = [
  { user: 'u16', name: 'Абенова Д. С.', senior: true, busy: null },
  { user: 'u17', name: 'Серикова А. М.', senior: true, busy: null },
  { user: 'u18', name: 'Жумабаев Н. К.', senior: false, busy: null },
  { user: 'u19', name: 'Ким О. В.', senior: false, busy: null },
  { user: 'u20', name: 'Тулеуова Д. А.', senior: true, busy: null },
  { user: 'u21', name: 'Омаров Е. Б.', senior: true, busy: null },
  { user: 'u22', name: 'Бекова С. Н.', senior: false, busy: null },
  { user: 'u23', name: 'Нурланов А. Т.', senior: false, busy: null },
  { user: 'u24', name: 'Исаева Г. Р.', senior: true, busy: null },
  { user: 'u25', name: 'Рахимов Т. С.', senior: false, busy: 'в рейсе 001 Астана — Алматы • свободен с 30.10 16:00' },
  { user: 'u26', name: 'Садыкова М. Е.', senior: false, busy: null },
  {
    user: 'u27',
    name: 'Абенов К. Ж.',
    senior: false,
    busy: 'в рейсе 015 Астана — Петропавловск • свободен с 29.10 06:00',
  },
]

function fakeTripCrew(tripId: string): TripCrew {
  const trip = FAKE_TRIPS.find((x) => x.id === tripId)
  return {
    trip: {
      id: tripId,
      train: trip?.train ?? '',
      name: trip?.routeName ?? '',
      state: trip?.state ?? 'PLANNED',
      departure: tripId === 'T2' ? fakeTime(-10) : (trip?.fact ?? trip?.planned ?? ''),
      arrival: tripId === 'T2' ? fakeTime(9) : '',
      departureStation: tripId === 'T2' ? 'ALA' : 'AST',
    },
    wagons: (FAKE_COMPOSITION[tripId] ?? []).map((number) => {
      const type = FAKE_WAGON_TYPES[number] ?? 'COMPARTMENT'
      return { number, type, passenger: PASSENGER_TYPES.includes(type) }
    }),
    crew: (FAKE_CREW[tripId] ?? []).map((m) => ({ ...m, wagons: [...m.wagons] })),
  }
}

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
