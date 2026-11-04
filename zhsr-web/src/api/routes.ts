import type { TableSort } from '@/shared/components/table/types'
import { http } from './http'

/**
 * API маршрутов (сервер zhsr-app: ListController — список, RouteController — карточка, создание, сохранение, станции
 * для следующей остановки и пересчёт остановок).
 */

/** Точка маршрута: станция, расстояние и время от предыдущей точки. */
export interface RoutePoint {
  /** станция — id справочника Station */
  station: number
  /** расстояние от начальной станции маршрута, км */
  km: number
  /** остановка (have_stop); false — станция следования, поезд проходит её без остановки */
  haveStop: boolean
  /** время в пути от предыдущей точки маршрута, мин; у начальной станции 0 */
  runDuration: number
  /** стоянка, мин; у станций следования, начальной и конечной станций 0 */
  stopDuration: number
}

/** Остановка маршрута в редакторе. */
export interface RouteStop {
  station: number
  /** расстояние от начальной станции маршрута, км */
  km: number
  /** время в пути от предыдущей остановки, мин; по умолчанию его подставляет сервер, пользователь может изменить */
  runDuration: number
  /** стоянка, мин */
  stopDuration: number
}

/** Атрибуты маршрута: общие у строки списка и у карточки. */
export interface RouteAttrs {
  id: number
  /** номер поезда */
  trainNumber: string
  /** сообщение: «Астана — Алматы» */
  name: string
  /** вид маршрута — id справочника RouteKind (константы RouteKind) */
  routeKind: number
  /** вид перевозки — id справочника TransportType */
  transportType: number
  /** дата ввода маршрута, ISO */
  dbeg: string
  /** дата завершения, ISO; null — маршрут действует бессрочно */
  dend: string | null
  /** время в пути, мин; вводит пользователь, сумма по станциям — подсказка в редакторе */
  duration: number
  /** норма часов проводника; null — не задана */
  conductorHours: number | null
  /** норма часов ЛНП; null — не задана */
  lnpHours: number | null
  /** норма часов ПЭМ; null — не задана */
  pemHours: number | null
}

/** Маршрут как вариант выбора (комбо «Маршрут»): маршрут и то, что строке варианта нужно от его станций. */
export interface RouteOption {
  id: number
  /** номер поезда */
  trainNumber: string
  /** сообщение: «Астана — Алматы» */
  name: string
  /** вид маршрута — id справочника RouteKind (константы RouteKind) */
  routeKind: number
  /** время в пути, мин */
  duration: number
  /** длина маршрута, км */
  km: number
  /** остановки по порядку — id справочника Station; станции следования не входят */
  stops: number[]
}

/** Строка таблицы маршрутов: вариант выбора маршрута и атрибуты. Все станции — в карточке (Route). */
export interface RouteRow extends RouteOption, RouteAttrs {}

/** Карточка маршрута: атрибуты и все станции маршрута. */
export interface Route extends RouteAttrs {
  /** все станции маршрута по порядку: остановки и станции следования */
  points: RoutePoint[]
}

/** Атрибуты нового маршрута, которые вводятся в диалоге «Новый маршрут». */
export interface RouteDraft {
  trainNumber: string
  name: string
  /** вид маршрута — id справочника RouteKind; null — ещё не выбран */
  routeKind: number | null
  /** дата ввода маршрута, ISO; null — ещё не задана */
  dbeg: string | null
}

/** Состояние маршрута для фильтра: действует или завершён. */
export type RouteState = 'active' | 'closed'

export interface RouteFilter {
  /** виды маршрута — id справочника RouteKind */
  kinds: number[]
  states: RouteState[]
  /** депо: маршрут идёт через станцию депо; null — без ограничения (сервер может ограничить по роли) */
  depot?: number | null
}

/**
 * Станция, на которую можно перейти от предыдущей остановки: расстояние от начала маршрута и время в пути от
 * предыдущей остановки по умолчанию.
 */
export interface NextStation {
  station: number
  /** расстояние от начала маршрута, км */
  km: number
  /** расстояние от предыдущей остановки, км */
  distance: number
  /** станции следования между предыдущей остановкой и этой станцией — id справочника Station */
  via: number[]
  /** время в пути от предыдущей остановки по умолчанию, мин */
  runDuration: number
}

/** Маршруты как варианты выбора: фильтр — как у списка маршрутов. */
export async function fetchRouteOptions(filter: RouteFilter): Promise<RouteOption[]> {
  return (await http.get<RouteOption[]>('/routes/options', { params: filter })).data
}

/** Список маршрутов: только то, что нужно строке списка. */
export async function fetchRoutes(filter: RouteFilter, sort: TableSort | null): Promise<RouteRow[]> {
  return (await http.get<RouteRow[]>('/routes', { params: { ...filter, sort: sort?.field, desc: sort?.desc } })).data
}

/** Карточка маршрута со всеми станциями. Маршрута нет — ошибка 404 с текстом «Маршрут не найден». */
export async function fetchRoute(id: number): Promise<Route> {
  return (await http.get<Route>(`/routes/${id}`)).data
}

/** Создать маршрут без остановок; остановки вводятся в редакторе маршрута. */
export async function createRoute(draft: RouteDraft): Promise<Route> {
  return (await http.post<Route>('/routes', draft)).data
}

/**
 * Сохранить маршрут из редактора. points — точки маршрута по порядку, как в редакторе: остановки и известные станции
 * следования; между соседними точками без перегона сервер прокладывает кратчайший путь и пересчитывает километраж.
 */
export async function saveRoute(route: Route, points: RoutePoint[]): Promise<Route> {
  const { id, ...attrs } = route
  return (await http.put<Route>(`/routes/${id}`, { ...attrs, points })).data
}

/**
 * Станции, на которые можно перейти от остановки from: расстояние от начала маршрута и время в пути от from по
 * умолчанию. Без from — станции, с которых может начинаться маршрут (узловые).
 */
export async function fetchNextStations(from: RouteStop | null): Promise<NextStation[]> {
  const params = from ? { station: from.station, km: from.km } : {}
  return (await http.get<NextStation[]>('/routes/next-stations', { params })).data
}

/**
 * Пересчитать остановки после правки в редакторе: расстояния от начала маршрута — у всех, время в пути по умолчанию —
 * только у остановок changed (у них сменилась станция или предыдущая остановка); у остальных время, введённое
 * пользователем, сохраняется.
 */
export async function recalcStops(stops: RouteStop[], changed: number[]): Promise<RouteStop[]> {
  return (await http.post<RouteStop[]>('/routes/recalc-stops', { stops, changed })).data
}

/** Действует ли маршрут сегодня. */
export function stateOf(route: RouteAttrs): RouteState {
  return !route.dend || route.dend >= new Date().toISOString().slice(0, 10) ? 'active' : 'closed'
}
