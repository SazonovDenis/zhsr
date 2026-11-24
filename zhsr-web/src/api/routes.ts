import type { TableSort } from '@/shared/components/table/types'
import { http } from './http'

/**
 * API маршрутов (сервер zhsr-app: ListController — список, RouteController — карточка, создание, сохранение, станции
 * для следующей остановки и пересчёт остановок).
 */

/**
 * Точка маршрута: станция, расстояние и время. Время — минуты от полуночи дня отправления с начальной станции; 1440 и
 * больше — следующие сутки.
 */
export interface RoutePoint {
  /** станция — id справочника Station */
  station: number
  /** расстояние от начальной станции маршрута, км */
  km: number
  /** остановка (have_stop); false — станция следования, поезд проходит её без остановки */
  haveStop: boolean
  /** прибытие, мин от полуночи; у начальной станции null */
  arrivalMin: number | null
  /** отправление, мин от полуночи; у конечной станции null; у станции следования равно прибытию */
  departureMin: number | null
}

/** Остановка маршрута в редакторе; время — как у точки маршрута. */
export interface RouteStop {
  station: number
  /** расстояние от начальной станции маршрута, км */
  km: number
  /** прибытие, мин от полуночи; у начальной станции null */
  arrivalMin: number | null
  /** отправление, мин от полуночи; у конечной станции null */
  departureMin: number | null
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
  /** норма часов проводника, бывает дробной; null — не задана */
  conductorHours: number | null
  /** норма часов ЛНП, бывает дробной; null — не задана */
  lnpHours: number | null
  /** норма часов ПЭМ, бывает дробной; null — не задана */
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
  /** время в пути, мин: прибытие на конечную станцию минус отправление с начальной */
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

export interface RouteFilter {
  /** виды маршрута — id справочника RouteKind */
  kinds: number[]
  /** состояния — константы ActiveState */
  states: number[]
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
 * умолчанию. Без from — все станции маршрутной сети.
 */
export async function fetchNextStations(from: RouteStop | null): Promise<NextStation[]> {
  const params = from ? { station: from.station, km: from.km } : {}
  return (await http.get<NextStation[]>('/routes/next-stations', { params })).data
}

/**
 * Пересчитать остановки после правки в редакторе: расстояния от начала маршрута — у всех, время в пути от предыдущей
 * остановки по умолчанию — только у остановок changed (у них сменилась станция или предыдущая остановка); у остальных
 * перегоны и стоянки сохраняются. Время прибытия и отправления сервер считает заново от отправления с начальной станции.
 */
export async function recalcStops(stops: RouteStop[], changed: number[]): Promise<RouteStop[]> {
  return (await http.post<RouteStop[]>('/routes/recalc-stops', { stops, changed })).data
}

/** Пара соседних остановок: станции — id справочника Station. */
export interface RouteLeg {
  from: number
  to: number
}

/** Станция следования между соседними остановками. */
export interface LegStation {
  station: number
  /** расстояние от предыдущей остановки, км */
  km: number
}

/** Станции следования кратчайшего пути для каждой пары соседних остановок, в порядке legs. */
export async function fetchLegs(legs: RouteLeg[]): Promise<LegStation[][]> {
  return (await http.post<LegStation[][]>('/routes/legs', legs)).data
}

/** Удалить маршрут: на него ссылаются другие записи — ошибка 409 с причиной. */
export async function deleteRoute(id: number): Promise<void> {
  await http.delete(`/routes/${id}`)
}
