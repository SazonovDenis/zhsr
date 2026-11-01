import type { TableSort } from '@/shared/components/table/types'
import { fakeSort } from './fakeSort'
// import { http } from './http'

/**
 * API маршрутов. Сервер пока не готов: функции работают с фиктивными данными в памяти, а над ними закомментирован
 * будущий вызов REST.
 */

/** Точка маршрута: станция, расстояние и время от предыдущей точки. */
export interface RoutePoint {
  /** станция — id справочника Station */
  station: string
  /** расстояние от начальной станции маршрута, км */
  km: number
  /** остановка; false — станция следования, поезд проходит её без остановки */
  stop: boolean
  /** время в пути от предыдущей точки маршрута, мин; у начальной станции 0 */
  runDuration: number
  /** стоянка, мин; у станций следования, начальной и конечной станций 0 */
  stopDuration: number
}

/** Остановка маршрута в редакторе. */
export interface RouteStop {
  station: string
  /** расстояние от начальной станции маршрута, км */
  km: number
  /** время в пути от предыдущей остановки, мин; по умолчанию его подставляет сервер, пользователь может изменить */
  runDuration: number
  /** стоянка, мин */
  stopDuration: number
}

export interface Route {
  id: string
  /** номер поезда */
  train: string
  /** сообщение: «Астана — Алматы» */
  name: string
  /** вид маршрута — id справочника RouteKind */
  kind: string
  /** вид перевозки на русском */
  transport: string
  /** дата ввода маршрута, ISO */
  since: string
  /** дата завершения, ISO; null — маршрут действует бессрочно */
  until: string | null
  /** время в пути, часов */
  durationHours: number
  /** норма часов проводника */
  hoursConductor: number
  /** норма часов ЛНП */
  hoursLnp: number
  /** норма часов ПЭМ */
  hoursPem: number
  /** все станции маршрута по порядку: остановки и станции следования */
  points: RoutePoint[]
}

/** Атрибуты нового маршрута, которые вводятся в диалоге «Новый маршрут». */
export interface RouteDraft {
  train: string
  name: string
  /** вид маршрута — id справочника RouteKind; null — ещё не выбран */
  kind: string | null
  since: string | null
}

/** Состояние маршрута для фильтра: действует или завершён. */
export type RouteState = 'active' | 'closed'

export interface RouteFilter {
  /** виды маршрута — id справочника RouteKind */
  kinds: string[]
  states: RouteState[]
}

/**
 * Станция, на которую можно перейти от предыдущей остановки: расстояние от начала маршрута и время в пути от
 * предыдущей остановки по умолчанию.
 */
export interface NextStation {
  station: string
  /** расстояние от начала маршрута, км */
  km: number
  /** расстояние от предыдущей остановки, км */
  distance: number
  /** станции следования между предыдущей остановкой и этой станцией — id справочника Station */
  via: string[]
  /** время в пути от предыдущей остановки по умолчанию, мин */
  runDuration: number
}

export async function fetchRoutes(filter: RouteFilter, sort: TableSort | null): Promise<Route[]> {
  // return (await http.get<Route[]>('/routes', { params: { ...filter, sort: sort?.field, desc: sort?.desc } })).data
  return Promise.resolve(
    fakeSort(
      FAKE_ROUTES.filter(
        (r) =>
          (filter.kinds.length === 0 || filter.kinds.includes(r.kind)) &&
          (filter.states.length === 0 || filter.states.includes(stateOf(r))),
      ),
      sort,
    ),
  )
}

export async function fetchRoute(id: string): Promise<Route | null> {
  // return (await http.get<Route>(`/routes/${id}`)).data
  return Promise.resolve(structuredClone(FAKE_ROUTES.find((r) => r.id === id) ?? null))
}

/** Создать маршрут без остановок; остановки вводятся в редакторе маршрута. */
export async function createRoute(draft: RouteDraft): Promise<Route> {
  // return (await http.post<Route>('/routes', draft)).data
  const route: Route = {
    id: `R${FAKE_ROUTES.length + 1}`,
    ...draft,
    kind: draft.kind ?? '',
    since: draft.since ?? '',
    transport: 'Пассажирский',
    until: null,
    durationHours: 0,
    hoursConductor: 0,
    hoursLnp: 0,
    hoursPem: 0,
    points: [],
  }
  FAKE_ROUTES.push(route)
  return Promise.resolve(structuredClone(route))
}

/** Сохранить маршрут; сервер заново строит станции следования между остановками. */
export async function saveRoute(route: Route, stops: RouteStop[]): Promise<Route> {
  // return (await http.put<Route>(`/routes/${route.id}`, { ...route, stops })).data
  const saved: Route = { ...route, points: fakePoints(stops) }
  const index = FAKE_ROUTES.findIndex((r) => r.id === route.id)
  if (index !== -1) FAKE_ROUTES[index] = saved
  return Promise.resolve(structuredClone(saved))
}

/**
 * Станции, на которые можно перейти от остановки from: расстояние от начала маршрута и время в пути от from по
 * умолчанию. Без from — станции, с которых может начинаться маршрут.
 */
export async function fetchNextStations(from: RouteStop | null): Promise<NextStation[]> {
  // return (await http.get<NextStation[]>('/routes/next-stations', { params: from ?? {} })).data
  if (!from) return Promise.resolve(HUBS.map((station) => ({ station, km: 0, distance: 0, via: [], runDuration: 0 })))
  const paths = shortestPaths(from.station)
  return Promise.resolve(
    [...paths.entries()]
      .filter(([station]) => station !== from.station)
      .sort(([, a], [, b]) => a.km - b.km)
      .map(([station, path]) => ({
        station,
        km: from.km + path.km,
        distance: path.km,
        via: path.via,
        runDuration: defaultRunDuration(path.km),
      })),
  )
}

/**
 * Пересчитать остановки после правки в редакторе: расстояния от начала маршрута — у всех, время в пути по умолчанию —
 * только у остановок changed (у них сменилась станция или предыдущая остановка); у остальных время, введённое
 * пользователем, сохраняется.
 */
export async function recalcStops(stops: RouteStop[], changed: number[]): Promise<RouteStop[]> {
  // return (await http.post<RouteStop[]>('/routes/recalc-stops', { stops, changed })).data
  let km = 0
  return Promise.resolve(
    stops.map((stop, i) => {
      const prev = stops[i - 1]
      const distance = prev ? (shortestPaths(prev.station).get(stop.station)?.km ?? 0) : 0
      km += distance
      const runDuration = i === 0 ? 0 : changed.includes(i) ? defaultRunDuration(distance) : stop.runDuration
      return { ...stop, km, runDuration }
    }),
  )
}

/** Действует ли маршрут сегодня. */
export function stateOf(route: Route): RouteState {
  return !route.until || route.until >= new Date().toISOString().slice(0, 10) ? 'active' : 'closed'
}

// --- фиктивные данные: станции и расстояния из макета ---

const HUBS = ['AST', 'KRG', 'ALA', 'PTR']

// перегоны: соседние станции и расстояние, км
const FAKE_EDGES: [string, string, number][] = [
  ['ALA', 'ALA1', 9],
  ['ALA1', 'OTAR', 156],
  ['OTAR', 'SHU', 155],
  ['SHU', 'SARYSHAGAN', 320],
  ['SARYSHAGAN', 'MOYINTY', 126],
  ['MOYINTY', 'KRG', 359],
  ['KRG', 'NURLY', 203],
  ['NURLY', 'AST', 15],
  ['AST', 'KOK', 296],
  ['KOK', 'PTR', 195],
]

// время в пути по умолчанию: средняя скорость 70 км/ч
function defaultRunDuration(km: number): number {
  return Math.round((km / 70) * 60)
}

/** Кратчайшие пути от станции: расстояние и станции следования по пути. */
function shortestPaths(from: string): Map<string, { km: number; via: string[] }> {
  const result = new Map<string, { km: number; via: string[] }>([[from, { km: 0, via: [] }]])
  const queue = [from]
  while (queue.length > 0) {
    const station = queue.shift()!
    const current = result.get(station)!
    for (const [a, b, km] of FAKE_EDGES) {
      const next = a === station ? b : b === station ? a : null
      if (!next) continue
      const known = result.get(next)
      const candidate = { km: current.km + km, via: station === from ? [] : [...current.via, station] }
      if (!known || candidate.km < known.km) {
        result.set(next, candidate)
        queue.push(next)
      }
    }
  }
  return result
}

/** Точки маршрута по остановкам: между остановками — станции следования по кратчайшему пути. */
function fakePoints(stops: RouteStop[]): RoutePoint[] {
  return stops.flatMap((stop, i) => {
    const prev = stops[i - 1]
    const last = i === stops.length - 1
    const stopPoint: RoutePoint = { ...stop, stop: true, stopDuration: i === 0 || last ? 0 : stop.stopDuration }
    if (!prev) return [stopPoint]

    // время в пути до остановки делится между перегонами пропорционально расстоянию
    const path = shortestPaths(prev.station).get(stop.station)
    const via = path?.via ?? []
    const total = stop.km - prev.km
    const passing: RoutePoint[] = []
    let km = prev.km
    let usedMin = 0
    let at = prev.station
    for (const station of via) {
      const segment = shortestPaths(at).get(station)?.km ?? 0
      km += segment
      const runDuration = total > 0 ? Math.round((stop.runDuration * segment) / total) : 0
      usedMin += runDuration
      passing.push({ station, km, stop: false, runDuration, stopDuration: 0 })
      at = station
    }
    return [...passing, { ...stopPoint, runDuration: Math.max(0, stop.runDuration - usedMin) }]
  })
}

function fakeRoute(
  id: string,
  attrs: Omit<Route, 'id' | 'points' | 'transport' | 'hoursConductor' | 'hoursLnp' | 'hoursPem'>,
  hours: [number, number, number],
  stops: [string, number][],
): Route {
  // остановки: станция и стоянка, мин; расстояния и время в пути — по перегонам
  let km = 0
  const routeStops: RouteStop[] = stops.map(([station, stopDuration], i) => {
    const prev = stops[i - 1]
    const distance = prev ? (shortestPaths(prev[0]).get(station)?.km ?? 0) : 0
    km += distance
    return { station, km, runDuration: defaultRunDuration(distance), stopDuration }
  })
  return {
    id,
    ...attrs,
    transport: 'Пассажирский',
    hoursConductor: hours[0],
    hoursLnp: hours[1],
    hoursPem: hours[2],
    points: fakePoints(routeStops),
  }
}

const FAKE_ROUTES: Route[] = [
  fakeRoute(
    'R1',
    { since: '2024-03-01', until: null, train: '1/2', name: 'Астана — Алматы', kind: 'REGULAR', durationHours: 19 },
    [19, 21, 21],
    [
      ['AST', 0],
      ['KRG', 15],
      ['SARYSHAGAN', 10],
      ['SHU', 20],
      ['ALA', 0],
    ],
  ),
  fakeRoute(
    'R2',
    { since: '2024-03-01', until: null, train: '2/1', name: 'Алматы — Астана', kind: 'REGULAR', durationHours: 19 },
    [19, 21, 21],
    [
      ['ALA', 0],
      ['SHU', 20],
      ['SARYSHAGAN', 10],
      ['KRG', 15],
      ['AST', 0],
    ],
  ),
  fakeRoute(
    'R3',
    {
      since: '2025-06-15',
      until: null,
      train: '77/78',
      name: 'Астана — Петропавловск — Астана',
      kind: 'REGULAR',
      durationHours: 16,
    },
    [16, 18, 18],
    [
      ['AST', 0],
      ['KOK', 10],
      ['PTR', 30],
      ['KOK', 10],
      ['NURLY', 5],
      ['AST', 0],
    ],
  ),
  fakeRoute(
    'R5',
    {
      since: '2026-10-20',
      until: null,
      train: '901/902',
      name: 'Астана — Караганды',
      kind: 'ONE_TIME',
      durationHours: 3,
    },
    [3, 4, 4],
    [
      ['AST', 0],
      ['KRG', 0],
    ],
  ),
  fakeRoute(
    'R6',
    {
      since: '2023-01-10',
      until: '2025-06-14',
      train: '81/82',
      name: 'Астана — Петропавловск',
      kind: 'REGULAR',
      durationHours: 8,
    },
    [8, 9, 9],
    [
      ['AST', 0],
      ['KOK', 10],
      ['PTR', 0],
    ],
  ),
]
