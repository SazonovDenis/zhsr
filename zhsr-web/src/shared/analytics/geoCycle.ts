import type { GeoChange, GeoChart, GeoResponsible, GeoRoute, GeoStop, GeoTrip } from '@/api/analytics'
import type { TripRef } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { TrainWagonChangeType, TrainWagonKind } from '@/shared/constants/TrainWagonChange'
import { WagonResponsibleKind } from '@/shared/constants/WagonResponsible'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import { routeBearing, routePlace, scheduleKm, valueAt } from './geoTrack'

/**
 * Цикл рейса на карте — общее карт «Текущие рейсы» и поезда: где на момент поезд, вагоны, которые ждут прицепки или
 * отцеплены, и проводники вне поезда. Время — миллисекунды; now — «сейчас», время загрузки данных: до него положения по
 * факту и координатам, после — ожидаемые по плану и расписанию. Места вагонов и проводников — только логические (по
 * журналам), координаты есть только у поезда в рейсе.
 */

const MINUTE = 60000

const ms = (iso: string) => new Date(iso).getTime()

/** Времена рейса: фактические, у неслучившегося — ожидаемые. */
export interface TripTimes {
  departure: number
  arrival: number
  /** начало цикла; предыдущего рейса нет — -Infinity */
  start: number
  /** конец цикла: прибытие или последняя запись цикла после него */
  end: number
  /** отставание от расписания, минут: после «сейчас» рейс идёт с ним */
  lag: number
}

export function tripTimes(trip: GeoTrip, now: number): TripTimes {
  const planned = ms(trip.plannedDt)
  // неотправленный в срок — отправится не раньше «сейчас»
  const departure = trip.departedDt ? ms(trip.departedDt) : Math.max(planned, now)
  const last = trip.track[trip.track.length - 1]
  const lag = trip.departedDt && last ? last[2] : (departure - planned) / MINUTE
  const arrival = trip.arrivedDt ? ms(trip.arrivedDt) : Math.max(ms(trip.arrival) + lag * MINUTE, now)
  return {
    departure,
    arrival,
    start: trip.cycleStart ? ms(trip.cycleStart) : -Infinity,
    end: Math.max(arrival, ...trip.changes.map((c) => ms(c.moment))),
    lag,
  }
}

/**
 * Что с рейсом на момент: цикл не начался (hidden), подготовка до отправления (prep), в пути (active), прибыл, но
 * записи цикла ещё впереди (tail), цикл закончен (done).
 */
export type TripPhase = 'hidden' | 'prep' | 'active' | 'tail' | 'done'

export function tripPhase(times: TripTimes, at: number): TripPhase {
  if (at < times.departure) return at > times.start ? 'prep' : 'hidden'
  if (at <= times.arrival) return 'active'
  return at <= times.end ? 'tail' : 'done'
}

export function routeLength(route: GeoRoute): number {
  return route.points[route.points.length - 1]?.[2] ?? 0
}

/** Поезд в месте карты. */
export interface TrainSpot {
  km: number
  /** широта и долгота */
  at: [number, number]
  /** направление хода, градусы; null — поезд не между концами маршрута в пути */
  bearing: number | null
  /** отставание от расписания, минут */
  delay: number
}

/** Километр, до которого известен ход рейса: последняя координата; до отправления — начало маршрута. */
export function knownKm(trip: GeoTrip): number {
  return trip.departedDt ? (trip.track[trip.track.length - 1]?.[1] ?? 0) : 0
}

/** Значок поезда на километре маршрута: между концами в пути — стрелкой, иначе кружком. */
export function spotAt(route: GeoRoute, km: number, moving: boolean, delay = 0): TrainSpot | null {
  const at = routePlace(route, km)
  if (!at) return null
  const between = km > 0 && km < routeLength(route) - 0.01
  return { km, at, bearing: moving && between ? routeBearing(route, km) : null, delay }
}

/**
 * Поезд рейса на момент: до отправления — в начале маршрута; в прошлом — по координатам (без них — в начале маршрута);
 * после «сейчас» — по расписанию с тем же отставанием; после прибытия — там, где рейс закончился.
 */
export function trainAt(chart: GeoChart, route: GeoRoute, trip: GeoTrip, at: number, now: number): TrainSpot | null {
  const times = tripTimes(trip, now)
  if (at < times.departure) return spotAt(route, 0, false)
  const length = routeLength(route)
  const last = trip.track[trip.track.length - 1]
  if (at >= times.arrival && (trip.arrivedDt || at > now)) {
    return spotAt(route, trip.arrivedDt ? (last?.[1] ?? length) : length, false, times.lag)
  }
  if (at <= now) {
    const value = valueAt(trip.track, (at - ms(chart.from)) / MINUTE)
    return spotAt(route, value?.[0] ?? 0, true, value?.[1] ?? 0)
  }
  const km = scheduleKm(route, (at - ms(trip.plannedDt)) / MINUTE - times.lag)
  return spotAt(route, Math.max(knownKm(trip), km), true, times.lag)
}

/** Вагон вне поезда: ждёт прицепки (wait) или отцеплен (left); change — запись, которая это определяет. */
export interface WagonStatus {
  state: 'wait' | 'left'
  change: GeoChange
}

/**
 * Вагон цикла рейса на момент: по факту действуют только факты, по плану — и ожидающие планы. null — вагон в поезде или
 * в цикле не участвует. later — id записей этого же момента, которые ещё не действуют (после выбранной в журнале).
 */
export function wagonStatus(
  trip: GeoTrip,
  wagon: number,
  at: number,
  plan: boolean,
  later: ReadonlySet<number> = new Set(),
): WagonStatus | null {
  const list = trip.changes.filter((c) => c.wagon.id === wagon)
  const applied = (c: GeoChange) =>
    (plan || c.kind === TrainWagonKind.FACT) && (ms(c.moment) < at || (ms(c.moment) === at && !later.has(c.id)))
  const next = list.find((c) => !applied(c))
  if (next?.changeType === TrainWagonChangeType.ATTACH) return { state: 'wait', change: next }
  const last = list.filter(applied).pop()
  return last?.changeType === TrainWagonChangeType.DETACH ? { state: 'left', change: last } : null
}

/** Вагон вне поезда на карте; unmarked — по факту он ещё в поезде: плановую отцепку, время которой прошло, не отметили. */
type ShownStatus = WagonStatus & { unmarked: boolean }

/**
 * Что с вагоном на карте: wagonStatus, а по факту ещё и вагон просроченной плановой отцепки — он остаётся на её станции
 * до конца цикла, как и в соседних записях журнала.
 */
function shownStatus(
  trip: GeoTrip,
  wagon: number,
  at: number,
  plan: boolean,
  later: ReadonlySet<number>,
): ShownStatus | null {
  const status = wagonStatus(trip, wagon, at, plan, later)
  if (status || plan) return status && { ...status, unmarked: false }
  const expected = wagonStatus(trip, wagon, at, true, later)
  return expected?.state === 'left' && expected.change.kind === TrainWagonKind.PLAN
    ? { ...expected, unmarked: true }
    : null
}

/** Место вагона вне поезда. */
interface WagonPlace {
  /** станция, на которой стоит значок, — id справочника Station */
  station: number
  /** где вагон, словами для фразы: «на станции …», «в депо …», «в парке отстоя …» */
  where: string
  /** рейс, в котором вагон сейчас на деле: на станции его нет; null — вагон на станции */
  away: TripRef | null
}

/** Знаки начала и конца жирной части строки подсказки: объект (станция, депо, парк отстоя) или номер рейса. */
export const BOLD_BEG = '\u0002'
export const BOLD_END = '\u0003'
const bold = (text: string) => `${BOLD_BEG}${text}${BOLD_END}`

const stationText = (station: number) =>
  t('на станции {name}', { name: bold(dictManager.getValue('Station', station)) })

/** Поезд не дальше стольких км от остановки считается стоящим на ней. */
const STOP_KM = 0.5

/**
 * Остановка, где проводник садится в поезд (board) или выходит из него, если поезд на километре km: садится на первой
 * остановке не позади поезда, вышел — на последней не впереди. Станции следования не в счёт.
 */
function stopFor(route: GeoRoute, km: number, board: boolean): GeoStop | null {
  const stops = route.stops.filter((x) => x.haveStop)
  const found = board ? stops.find((x) => x.km >= km - STOP_KM) : stops.filter((x) => x.km <= km + STOP_KM).pop()
  return found ?? (board ? stops[stops.length - 1] : stops[0]) ?? null
}

/**
 * По плану — станция записи; по факту — станция, где вагон на момент по журналу расположения (депо и парк отстоя — их
 * станция). Вагон по факту в другом рейсе — на станции записи, с этим рейсом.
 */
function wagonPlace(
  chart: GeoChart,
  trip: GeoTrip,
  status: WagonStatus,
  at: number,
  plan: boolean,
  now: number,
): WagonPlace {
  const res: WagonPlace = { station: status.change.station, where: stationText(status.change.station), away: null }
  if (plan) return res
  const moment = Math.min(at, now)
  const location = chart.locations.find(
    (x) => x.wagon === status.change.wagon.id && ms(x.dbeg) <= moment && (!x.dend || ms(x.dend) > moment),
  )
  if (location?.station) {
    res.station = location.station
    res.where = location.depot
      ? t('в депо {name}', { name: bold(dictManager.getValue('Depot', location.depot)) })
      : location.yard
        ? t('в парке отстоя {name}', { name: bold(dictManager.getValue('Yard', location.yard)) })
        : stationText(location.station)
  } else if (location?.trip && location.trip.id !== trip.id) res.away = location.trip
  return res
}

function stationAt(chart: GeoChart, station: number): [number, number] | null {
  const point = chart.stations.find((x) => x.station === station)?.point
  return point ? [point.y, point.x] : null
}

/** Что показать значком вне поезда: вагон, который ждёт прицепки или отцеплен, или проводник. */
export interface CycleItem {
  kind: 'wait' | 'left' | 'usr'
  /** широта и долгота */
  at: [number, number]
  /** вагона нет на этой станции: он в другом рейсе */
  pale: boolean
  /** кто: вагон или проводник */
  name: string
  /** где, словами для фразы: «на станции …», «в депо …» или «в парке отстоя …» */
  where: string
  /** рейс цикла: «2/1 от 24.11 14:17» */
  trip: string
  /** рейс цикла впереди: вагон ждёт прицепки к нему, проводник — заступления; иначе — после него */
  to: boolean
  /** рейс, в котором вагон на самом деле; пусто — вагон на месте */
  away: string
  /**
   * план прицепки или отцепки, время которого прошло, не отмечен: ждут этой прицепки или отцеплен этой отцепкой; у
   * проводников нет
   */
  overdue: boolean
  /** плановое время того, чего ждут или после чего остались: прицепки, отцепки, начала или конца приписки */
  planDt: string | null
  /** фактическое время того же; показывается, когда плана не было; null — неизвестно */
  factDt: string | null
}

/** Значок карты: всё одного вида в одном месте. */
export interface CycleIcon {
  key: string
  kind: CycleItem['kind']
  at: [number, number]
  pale: boolean
  overdue: boolean
  items: CycleItem[]
}

export function groupIcons(items: CycleItem[]): CycleIcon[] {
  const res = new Map<string, CycleIcon>()
  for (const item of items) {
    const key = `${item.kind}:${item.overdue}:${item.pale}:${item.at[0].toFixed(4)},${item.at[1].toFixed(4)}`
    const icon = res.get(key)
    if (icon) icon.items.push(item)
    else res.set(key, { key, kind: item.kind, at: item.at, pale: item.pale, overdue: item.overdue, items: [item] })
  }
  return [...res.values()]
}

/** Строка подсказки значка; head — заголовок. Жирные части — между BOLD_BEG и BOLD_END. */
export interface IconHintLine {
  text: string
  head: boolean
  /** время строки списка отдельной колонкой, чтобы времена соседних строк были выровнены; пусто — колонки нет */
  time: string
}

/** Что делают: для одного и для нескольких. */
const PHRASES = {
  wait: ['ждёт прицепки к рейсу {trip}', 'ждут прицепки к рейсу {trip}'],
  left: ['отцеплен от рейса {trip}', 'отцеплены от рейса {trip}'],
  duty: ['ждёт заступления на рейс {trip}', 'ждут заступления на рейс {trip}'],
  stayed: ['остаётся после рейса {trip}', 'остаются после рейса {trip}'],
} as const

const capitalized = (text: string) => text.charAt(0).toUpperCase() + text.slice(1)

function groupBy<T>(list: T[], key: (item: T) => string): T[][] {
  const res = new Map<string, T[]>()
  for (const item of list) {
    const group = res.get(key(item))
    if (group) group.push(item)
    else res.set(key(item), [item])
  }
  return [...res.values()]
}

/**
 * Подсказка значка по местам: «На станции … ждут прицепки к рейсу …» и список; у нескольких групп в одном месте — место
 * отдельной строкой, под ним фраза и список каждой группы. В списке — вагоны или ФИО с плановым временем, без плана — с
 * фактическим: «Вагон 15024, в 25.11 19:35». Жирным в фразах — станция, депо, парк отстоя и рейс от номера до даты.
 */
export function iconHint(icon: CycleIcon): IconHintLine[] {
  // колонка времени — только когда проводников больше двух; иначе время сразу за именем
  const columns = icon.kind === 'usr' && icon.items.length > 2
  return groupBy(icon.items, (x) => x.where).flatMap((place) => {
    const where = capitalized(place[0]!.where)
    const groups = groupBy(place, (x) => [x.trip, x.to, x.away, x.overdue].join('\n'))
    const lines = groups.flatMap((items) => {
      const { kind, trip, to, away, overdue } = items[0]!
      const phrase = t(PHRASES[kind === 'usr' ? (to ? 'duty' : 'stayed') : kind][items.length > 1 ? 1 : 0], { trip })
      const notes = [
        overdue ? t(kind === 'wait' ? 'прицепка по плану не отмечена' : 'отцепка по плану не отмечена') : '',
        away ? t('а сейчас в рейсе {trip}', { trip: away }) : '',
      ]
      const action = [phrase, ...notes].filter(Boolean).join(', ')
      return [
        { text: groups.length > 1 ? capitalized(action) : `${where} ${action}`, head: true, time: '' },
        ...items.map((x) => {
          const time = timeText(x)
          const name = time ? `${x.name},` : x.name
          return columns
            ? { text: name, head: false, time }
            : { text: [name, time].filter(Boolean).join(' '), head: false, time: '' }
        }),
      ]
    })
    return groups.length > 1 ? [{ text: where, head: true, time: '' }, ...lines] : lines
  })
}

/** Вагоны и проводники цикла рейса вне поезда на момент; plan и later — как у wagonStatus. */
export function cycleItems(
  chart: GeoChart,
  trip: GeoTrip,
  at: number,
  plan: boolean,
  now: number,
  later: ReadonlySet<number> = new Set(),
): CycleItem[] {
  const res: CycleItem[] = []
  const route = chart.routes.find((r) => r.id === trip.route)
  const tripText = bold(
    t('{number} от {time}', { number: route?.trainNumber ?? '', time: formatDateTime(trip.plannedDt) }),
  )
  for (const wagon of new Map(trip.changes.map((c) => [c.wagon.id, c.wagon])).values()) {
    const status = shownStatus(trip, wagon.id, at, plan, later)
    if (!status) continue
    // неотмеченная отцепка — на станции плана
    const place = wagonPlace(chart, trip, status, at, plan || status.unmarked, now)
    const point = stationAt(chart, place.station)
    if (!point) continue
    res.push({
      kind: status.state,
      at: point,
      pale: !!place.away,
      name: t('Вагон {number}', { number: wagon.number }),
      where: place.where,
      trip: tripText,
      to: status.state === 'wait',
      away: place.away ? bold(tripRefDatedText(place.away)) : '',
      overdue: status.change.kind === TrainWagonKind.PLAN && ms(status.change.dt) < now,
      ...changeTimes(status.change),
    })
  }
  if (!route) return res

  /**
   * Где проводник с вагоном wagon на момент moment. Вагон вне поезда — там же, где вагон. Вагон в поезде: проводник
   * садится (join = true) или вышел (false) на остановке по месту поезда; join = null — едет с поездом и отдельно не
   * виден.
   */
  const withWagon = (wagon: number, moment: number, byPlan: boolean, join: boolean | null): DutyPlace | null => {
    const same = byPlan === plan && moment === at
    const status = shownStatus(trip, wagon, moment, byPlan, same ? later : new Set())
    if (status) {
      const place = wagonPlace(chart, trip, status, moment, byPlan || status.unmarked, now)
      const point = stationAt(chart, place.station)
      return point ? { at: point, where: place.where, to: status.state === 'wait', change: status.change } : null
    }
    const spot = join === null ? null : trainAt(chart, route, trip, moment, now)
    const stop = spot && stopFor(route, spot.km, join!)
    const point = stop && routePlace(route, stop.km)
    return point ? { at: point, where: stationText(stop.station), to: join!, change: null } : null
  }

  const periods = responsiblePeriods(chart, trip, now)
  for (const usr of trip.conductors) {
    const own = periodsOf(periods, usr.id)
    const current = own.find((p) => p.from <= at && p.to > at)
    // приписка начнётся — ждёт там, где тогда будет вагон; закончилась — остался там, где вагон был. У просроченного
    // плана место — на его плановое время, а не на «сейчас», с которого он действует
    const next = current ? undefined : own.find((p) => p.from > at)
    const ended = own
      .filter((p) => p.to <= at)
      .sort((a, b) => a.to - b.to)
      .pop()
    let place: DutyPlace | null = null
    if (current) place = withWagon(current.wagon, at, plan, null)
    else if (next) place = withWagon(next.wagon, next.placeFrom, next.placeFrom > now, true)
    else if (ended) place = withWagon(ended.wagon, ended.placeTo - 1, ended.placeTo > now, false)
    if (!place) continue
    const times = dutyTimes(place.to, current ?? next, current ?? ended)
    res.push({
      kind: 'usr',
      at: place.at,
      pale: false,
      name: usr.name,
      where: place.where,
      trip: tripText,
      // с ждущим вагоном и до приписки — заступление на рейс, с отцепленным и после приписки — после рейса
      to: place.to,
      away: '',
      overdue: false,
      // у приписки без своего времени (открытая, а вагон отцеплен) — время записи вагона
      ...(times.planDt || times.factDt || !place.change ? times : changeTimes(place.change)),
    })
  }
  return res
}

/** Место проводника; to — он ждёт рейса, а не остался после него; change — запись вагона, с которым он вне поезда. */
interface DutyPlace {
  at: [number, number]
  where: string
  to: boolean
  change: GeoChange | null
}

/** Плановое и фактическое время записи журнала для подсказки. */
function changeTimes(change: GeoChange) {
  return { planDt: change.planDt, factDt: change.kind === TrainWagonKind.FACT ? change.dt : null }
}

/** Времена приписки для подсказки: перед рейсом (to) — начала приписки starting, после — конца приписки ending. */
function dutyTimes(to: boolean, starting: Period | undefined, ending: Period | undefined) {
  const fact = to ? starting?.from : ending?.to
  return {
    planDt: (to ? starting?.planFrom : ending?.planTo) ?? null,
    factDt: fact === undefined || !Number.isFinite(fact) ? null : new Date(fact).toISOString(),
  }
}

/** Время строки подсказки: плановое, а без плана — фактическое; неизвестно — пусто. */
function timeText(item: CycleItem): string {
  const time = item.planDt ?? item.factDt
  return time ? t('в {time}', { time: formatDateTime(time) }) : ''
}

/** Приписка к вагону за период [from, to). */
interface Period {
  wagon: number
  from: number
  to: number
  usrs: number[]
  /** время, на которое берётся место начала и конца: у просроченного плана — плановое, а не «сейчас» */
  placeFrom: number
  placeTo: number
  /** плановое время начала и конца приписки; null — неизвестно */
  planFrom: string | null
  planTo: string | null
}

/**
 * Приписки к вагонам поезда в цикле рейса: периоды факта, а ожидающий план с его времени (не раньше «сейчас») сменяет
 * ответственных вагона.
 */
function responsiblePeriods(chart: GeoChart, trip: GeoTrip, now: number): Period[] {
  const times = tripTimes(trip, now)
  const windowStart = Math.max(times.start, ms(chart.from))
  const wagons = new Set([
    ...trip.changes.map((c) => c.wagon.id),
    ...chart.states
      .filter((s) => s.train === trip.train.id && ms(s.dbeg) < times.end && (!s.dend || ms(s.dend) > windowStart))
      .map((s) => s.wagon),
  ])
  const byWagon = new Map<number, GeoResponsible[]>()
  for (const r of chart.responsibles) {
    if (!wagons.has(r.wagon)) continue
    const list = byWagon.get(r.wagon)
    if (list) list.push(r)
    else byWagon.set(r.wagon, [r])
  }
  const res: Period[] = []
  for (const [wagon, list] of byWagon) {
    let periods: Period[] = list
      .filter((r) => r.kind === WagonResponsibleKind.FACT)
      .map((r) => ({
        wagon,
        from: ms(r.dbeg),
        to: r.dend ? ms(r.dend) : Infinity,
        usrs: r.usrs,
        placeFrom: ms(r.dbeg),
        placeTo: r.dend ? ms(r.dend) : Infinity,
        planFrom: r.planDt,
        // конец периода — начало сменившего его
        planTo: list.find((x) => x.kind === WagonResponsibleKind.FACT && x.dbeg === r.dend)?.planDt ?? null,
      }))
    const plans = list.filter((r) => r.kind === WagonResponsibleKind.PLAN).sort((a, b) => ms(a.dbeg) - ms(b.dbeg))
    for (const plan of plans) {
      const from = Math.max(ms(plan.dbeg), now)
      const planned = ms(plan.dbeg)
      periods = periods
        .map((p) =>
          p.to > from
            ? { ...p, to: Math.max(p.from, from), placeTo: Math.max(p.placeFrom, planned), planTo: plan.dbeg }
            : p,
        )
        .filter((p) => p.to > p.from)
      periods.push({
        wagon,
        from,
        to: Infinity,
        usrs: plan.usrs,
        placeFrom: planned,
        placeTo: Infinity,
        planFrom: plan.dbeg,
        planTo: null,
      })
    }
    res.push(...periods.filter((p) => p.to > windowStart && p.from < times.end))
  }
  return res
}

/** Приписки сотрудника по времени начала; идущие подряд к одному вагону — одна. */
function periodsOf(periods: Period[], usr: number): Period[] {
  const res: Period[] = []
  for (const p of periods.filter((x) => x.usrs.includes(usr)).sort((a, b) => a.from - b.from)) {
    const same = res.find((x) => x.wagon === p.wagon && x.to >= p.from)
    if (same) {
      if (p.to > same.to) {
        same.planTo = p.planTo
        same.placeTo = p.placeTo
      }
      same.to = Math.max(same.to, p.to)
    } else res.push({ ...p })
  }
  return res
}
