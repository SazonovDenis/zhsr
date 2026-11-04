import type { CrewMember, TripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import { CrewDuty } from '@/shared/constants/systemDicts'

/**
 * Норма проводников рейса (Ф-РЕЙ-06): 2 проводника на пассажирский вагон или 3 на два соседних вагона; один проводник
 * обслуживает 1 вагон или 2 соседних; в хвостовые вагоны (первый и последний вагоны состава) — по 2 старших
 * проводника. Вагоны проводника, ставшие несоседними, в норму не засчитываются.
 */

export interface WagonNorm {
  number: string
  /** позиция в составе, с 1 */
  pos: number
  /** тип вагона — id справочника WagonType */
  type: number
  passenger: boolean
  /** хвостовой пассажирский вагон: первый или последний в составе */
  tail: boolean
  /** проводники вагона по порядку назначения */
  conductors: CrewMember[]
  /** норма вагона выполнена */
  ok: boolean
  /** в хвостовом вагоне не хватает старших проводников */
  needSeniors: boolean
  /** у проводника этого вагона два несоседних вагона */
  notAdjacent: boolean
}

export interface CrewNorm {
  wagons: WagonNorm[]
  /** назначено проводников в счёт нормы и сколько нужно */
  have: number
  need: number
  /** все пассажирские вагоны по норме */
  full: boolean
  /** хвостовые вагоны без двух старших проводников */
  tailBad: string[]
  /** пассажирские вагоны без нормы */
  notOk: string[]
  /** проводники рейса без вагона */
  pool: CrewMember[]
  /** назначены начальник поезда и ПЭМ */
  chiefAndPem: boolean
}

export function crewNorm(crew: TripCrew): CrewNorm {
  // отцепленные вагоны в составе не едут
  const live = crew.wagons.filter((w) => !w.detached)
  const order = live.map((w) => w.number)
  const conductors = crew.crew.filter((m) => m.crewDutyType === CrewDuty.CONDUCTOR)
  const last = order.length - 1
  const adjacent = (m: CrewMember) =>
    m.wagons.length < 2 || Math.abs(order.indexOf(m.wagons[0]!) - order.indexOf(m.wagons[1]!)) === 1
  const serving = (number: string) => conductors.filter((m) => m.wagons.includes(number))

  const ok = new Set<string>()
  const seen = new Set<string>()
  let have = 0
  let need = 0
  for (const w of live.filter((x) => x.passenger)) {
    if (seen.has(w.number)) continue
    // вагон и соседний, который обслуживает общий проводник, считаются вместе: 3 проводника на два вагона
    const shared = conductors.find((m) => m.wagons.length === 2 && m.wagons.includes(w.number) && adjacent(m))
    const group = shared ? [...shared.wagons] : [w.number]
    group.forEach((n) => seen.add(n))
    const people = new Set(
      group.flatMap((n) =>
        serving(n)
          .filter(adjacent)
          .map((m) => m.usr),
      ),
    )
    const n = group.length === 2 ? 3 : 2
    need += n
    have += Math.min(n, people.size)
    if (people.size >= n) group.forEach((x) => ok.add(x))
  }

  const wagons: WagonNorm[] = live.map((w, i) => {
    const list = serving(w.number)
    const tail = w.passenger && (i === 0 || i === last)
    const needSeniors = tail && list.filter((m) => m.senior).length < 2
    const notAdjacent = list.some((m) => !adjacent(m))
    return {
      number: w.number,
      pos: i + 1,
      type: w.wagonType,
      passenger: w.passenger,
      tail,
      conductors: list,
      ok: !w.passenger || (ok.has(w.number) && !needSeniors && !notAdjacent),
      needSeniors,
      notAdjacent,
    }
  })

  const passenger = wagons.filter((w) => w.passenger)
  return {
    wagons,
    have,
    need,
    full: passenger.every((w) => ok.has(w.number)),
    tailBad: wagons.filter((w) => w.needSeniors).map((w) => w.number),
    notOk: passenger.filter((w) => !w.ok).map((w) => w.number),
    pool: conductors.filter((m) => m.wagons.length === 0),
    chiefAndPem:
      crew.crew.some((m) => m.crewDutyType === CrewDuty.LNP) && crew.crew.some((m) => m.crewDutyType === CrewDuty.PEM),
  }
}

/** Норма выполнена полностью: все вагоны по норме, в хвостовых — старшие проводники. */
export function normDone(norm: CrewNorm): boolean {
  return norm.full && norm.tailBad.length === 0 && norm.notOk.length === 0
}

/**
 * Кого можно поставить в место вагона: текущего, нераспределённых и проводников соседнего вагона с одним вагоном.
 * label — подпись проводника.
 */
export function slotOptions(
  crew: TripCrew,
  w: WagonNorm,
  slot: number,
  label: (m: CrewMember) => string,
): { user: number; label: string }[] {
  const order = crew.wagons.filter((x) => !x.detached).map((x) => x.number)
  const i = order.indexOf(w.number)
  const neighbours = [order[i - 1], order[i + 1]].filter((x): x is string => !!x)
  const current = w.conductors[slot]
  const all = crew.crew.filter((m) => m.crewDutyType === CrewDuty.CONDUCTOR)
  return [
    ...(current
      ? [{ user: current.usr, label: label(current) + (current.wagons.length === 2 ? ` • ${t('2 вагона')}` : '') }]
      : []),
    ...all.filter((m) => m.wagons.length === 0).map((m) => ({ user: m.usr, label: label(m) })),
    ...all
      .filter((m) => m.wagons.length === 1 && neighbours.includes(m.wagons[0]!) && !m.wagons.includes(w.number))
      .map((m) => ({ user: m.usr, label: `${label(m)} • ${t('с вагона {n}', { n: m.wagons[0]! })}` })),
  ]
}
