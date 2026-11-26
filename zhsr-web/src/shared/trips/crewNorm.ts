import type { CrewMember, TripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import { CrewDuty } from '@/shared/constants/systemDicts'
import { userName } from '@/shared/user'

/**
 * Норма проводников рейса (Ф-РЕЙ-06): 2 проводника на пассажирский вагон или 3 на два соседних вагона; один проводник
 * обслуживает 1 вагон или 2 соседних; в хвостовые вагоны (первый и последний вагоны состава) — по 2 старших
 * проводника. Проводник с двумя несоседними вагонами или больше чем с двумя вагонами в норму не засчитывается.
 */

/**
 * Нарушено ли правило «проводник — один вагон или два соседних»: positions — позиции его вагонов в составе. Возвращает
 * текст нарушения; пусто — правило выполнено.
 */
export function conductorWagonsError(positions: number[]): string {
  if (positions.length > 2) return t('назначен больше чем на два вагона')
  if (positions.length === 2 && Math.abs(positions[0]! - positions[1]!) !== 1) return t('назначен на несоседние вагоны')
  return ''
}

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
  /** норма вагона выполнена: issues пуст */
  ok: boolean
  /** в хвостовом вагоне не хватает старших проводников */
  needSeniors: boolean
  /** что не выполнено по норме у вагона — готовые тексты (переведены) */
  issues: string[]
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
  /** вагоны с невыполненной нормой */
  notOk: string[]
  /** проводники рейса без вагона */
  pool: CrewMember[]
  /** назначены начальник поезда и ПЭМ */
  chiefAndPem: boolean
}

export function crewNorm(crew: TripCrew): CrewNorm {
  // норма — по вагонам, которые едут в поезде
  const live = crew.wagons.filter((w) => w.riding)
  const order = live.map((w) => w.number)
  const conductors = crew.crew.filter((m) => m.crewDutyType === CrewDuty.CONDUCTOR)
  const last = order.length - 1
  const wagonsError = (m: CrewMember) => conductorWagonsError(m.wagons.map((n) => order.indexOf(n)))
  const adjacent = (m: CrewMember) => !wagonsError(m)
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
          .map((m) => m.usr.id),
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
    const seniors = list.filter((m) => m.senior).length
    const needSeniors = tail && seniors < 2
    const issues: string[] = []
    if (w.passenger && !ok.has(w.number)) {
      issues.push(t('Не хватает проводников: нужно 2 на вагон или 3 на два соседних'))
    }
    if (needSeniors) issues.push(t('Хвостовой вагон: нужны 2 старших проводника, назначено {n}', { n: seniors }))
    for (const m of list.filter((x) => !adjacent(x))) {
      issues.push(`${userName(m.usr)}: ${wagonsError(m)} (${m.wagons.join(', ')})`)
    }
    return {
      number: w.number,
      pos: i + 1,
      type: w.wagonType,
      passenger: w.passenger,
      tail,
      conductors: list,
      ok: issues.length === 0,
      needSeniors,
      issues,
    }
  })

  const passenger = wagons.filter((w) => w.passenger)
  return {
    wagons,
    have,
    need,
    full: passenger.every((w) => ok.has(w.number)),
    tailBad: wagons.filter((w) => w.needSeniors).map((w) => w.number),
    notOk: wagons.filter((w) => !w.ok).map((w) => w.number),
    pool: conductors.filter((m) => m.wagons.length === 0),
    chiefAndPem:
      crew.crew.some((m) => m.crewDutyType === CrewDuty.LNP) && crew.crew.some((m) => m.crewDutyType === CrewDuty.PEM),
  }
}
