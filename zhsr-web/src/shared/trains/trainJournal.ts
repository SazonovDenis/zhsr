import type { TrainCard, TrainChange, TrainState, TrainWagon } from '@/api/trains'
import type { TripRef } from '@/api/wagons'
import { TripStatus } from '@/shared/constants/systemDicts'
import { t } from '@/boot/i18n'
import {
  TrainJournalCode,
  TrainWagonChangeType,
  TrainWagonKind,
  TrainWagonPlanStatus,
} from '@/shared/constants/TrainWagonChange'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'

/**
 * Журнал поезда и состав на момент (техника «журнал и состояние», doc/db-doc/journal-state-plan-fact.md) — общее для
 * экранов поезда, рейса и телефона. Журнал — записи train_wagon_change и train_wagon_seq: факты и ожидающие планы.
 * Состав — записи
 * train_wagon: слой «факт» — история, слой «план» — ожидаемый состав. Справочник Station готовит страница.
 */

export const isFact = (c: TrainChange) => c.kind === TrainWagonKind.FACT
export const isWaiting = (c: TrainChange) =>
  c.kind === TrainWagonKind.PLAN && c.planStatus === TrainWagonPlanStatus.WAITING
export const isAttach = (c: TrainChange) => c.changeType === TrainWagonChangeType.ATTACH
/** Запись смены порядка вагонов. */
export const isSeq = (c: TrainChange) => c.code === TrainJournalCode.TRAIN_WAGON_SEQ

/** Ключ записи журнала на экране: id двух таблиц независимы. */
export const changeKey = (c: Pick<TrainChange, 'code' | 'id'>) => `${c.code}:${c.id}`

/** Порядок записей одного времени: смена порядка — после прицепок и отцепок (как на сервере); затем по id. */
const sameTime = (a: TrainChange, b: TrainChange) => Number(isSeq(a)) - Number(isSeq(b)) || a.id - b.id

/** Порядок записей журнала: по времени, в один момент — sameTime. */
export const byJournal = (a: TrainChange, b: TrainChange) =>
  new Date(a.dt).getTime() - new Date(b.dt).getTime() || sameTime(a, b)

/** Вагоны записи — id: вагон прицепки или отцепки, все вагоны нового порядка. */
export const changeWagons = (c: TrainChange): number[] =>
  c.wagon ? [c.wagon.id] : c.trainWagonSeqItem.map((x) => x.wagon.id)

/** Номера вагонов записи через запятую: вагон прицепки или отцепки, вагоны нового порядка по позициям. */
export const changeNumbers = (c: TrainChange): string =>
  c.wagon ? c.wagon.number : c.trainWagonSeqItem.map((x) => x.wagon.number).join(', ')

/**
 * Переставить вагоны списка в порядке wanted (как TrainJournal.reorder на сервере): вагоны из wanted меняются местами
 * между собой на занятых ими позициях, остальные остаются на местах.
 */
export function reorderBy<T>(list: T[], wagon: (item: T) => number, wanted: number[]): T[] {
  const moved = list
    .filter((x) => wanted.includes(wagon(x)))
    .sort((a, b) => wanted.indexOf(wagon(a)) - wanted.indexOf(wagon(b)))
  let next = 0
  return list.map((x) => (wanted.includes(wagon(x)) ? moved[next++]! : x))
}

/** Вагон состава на момент: запись участия и вагон с текущим расположением. */
export interface MomentWagon {
  state: TrainState
  wagon: TrainWagon
}

/** Время, с которого действует ожидаемый состав по плану: план, но не раньше последнего факта поезда. */
export function planMoment(card: TrainCard, c: TrainChange): string {
  const last = card.lastFactDt
  return last && new Date(c.dt) < new Date(last) ? last : c.dt
}

/** Состав поезда слоя kind на момент time (ISO), по позиции. */
export function compositionAt(card: TrainCard, kind: number, time: string): MomentWagon[] {
  const at = new Date(time).getTime()
  const wagons = new Map(card.trainWagons.map((w) => [w.wagon, w]))
  return card.states
    .filter(
      (s) =>
        s.kind === kind && new Date(s.dbeg).getTime() <= at && (s.dend === null || new Date(s.dend).getTime() > at),
    )
    .sort((a, b) => a.seq - b.seq)
    .flatMap((s) => {
      const wagon = wagons.get(s.wagon)
      return wagon ? [{ state: s, wagon }] : []
    })
}

/**
 * Состав после записи журнала c: у факта — по факту, у плана — ожидаемый. Записи одного момента применяются по одной в
 * порядке журнала (как TrainJournal на сервере): состав до момента, затем записи момента до c включительно. Прицепка
 * ставит вагон на свою позицию (больше числа вагонов — в конец), отцепка убирает.
 */
export function compositionAfter(card: TrainCard, c: TrainChange): MomentWagon[] {
  return compose(card, c, true)
}

/** Вагоны, у которых смена порядка c меняет позицию, — id. */
export function movedBySeq(card: TrainCard, c: TrainChange): Set<number> {
  const before = compose(card, c, false).map((m) => m.wagon.wagon)
  return new Set(
    compose(card, c, true)
      .map((m) => m.wagon.wagon)
      .filter((wagon, i) => before[i] !== wagon),
  )
}

/** Состав на момент записи c: с самой записью (self) или перед ней. */
function compose(card: TrainCard, c: TrainChange, self: boolean): MomentWagon[] {
  const fact = isFact(c)
  const kind = fact ? TrainWagonKind.FACT : TrainWagonKind.PLAN
  const momentOf = (x: TrainChange) => (fact ? x.dt : planMoment(card, x))
  const at = new Date(momentOf(c)).getTime()
  const journal = journalOf(card)
  const same = (fact ? journal.facts : journal.plans).filter((x) => new Date(momentOf(x)).getTime() === at)
  let order = compositionAt(card, kind, new Date(at - 1).toISOString()).map((m) => m.wagon)
  const wagons = new Map(card.trainWagons.map((w) => [w.wagon, w]))
  const key = changeKey(c)
  for (const x of same.slice(0, same.findIndex((y) => changeKey(y) === key) + (self ? 1 : 0))) {
    if (!x.wagon) {
      order = reorderBy(order, (w) => w.wagon, changeWagons(x))
      continue
    }
    const wagonId = x.wagon.id
    const index = order.findIndex((w) => w.wagon === wagonId)
    if (isAttach(x)) {
      const wagon = wagons.get(wagonId)
      if (index >= 0 || !wagon) continue
      order.splice(Math.min(Math.max(x.seq ?? order.length + 1, 1), order.length + 1) - 1, 0, wagon)
    } else if (index >= 0) {
      order.splice(index, 1)
    }
  }
  const dbeg = new Date(at).toISOString()
  return order.map((wagon, i) => ({ wagon, state: { id: 0, wagon: wagon.wagon, kind, seq: i + 1, dbeg, dend: null } }))
}

/**
 * Ожидаемый состав на момент time (ISO): до последнего факта поезда — состав по факту (история), с него — ожидаемый по
 * плану. Слой «план» начинается с последнего факта: раньше него в нём только записи, открытые и сейчас.
 */
export function expectedAt(card: TrainCard, time: string): MomentWagon[] {
  const last = card.lastFactDt
  const before = !!last && new Date(time).getTime() < new Date(last).getTime()
  return compositionAt(card, before ? TrainWagonKind.FACT : TrainWagonKind.PLAN, time)
}

/** Текущий состав по факту: открытые записи слоя «факт», по позиции. */
export function currentComposition(card: TrainCard): MomentWagon[] {
  const wagons = new Map(card.trainWagons.map((w) => [w.wagon, w]))
  return card.states
    .filter((s) => s.kind === TrainWagonKind.FACT && s.dend === null)
    .sort((a, b) => a.seq - b.seq)
    .flatMap((s) => {
      const wagon = wagons.get(s.wagon)
      return wagon ? [{ state: s, wagon }] : []
    })
}

/** Подпись плашки состава и карты поезда: «по факту на 24.11 19:07» или «ожидаемый по плану на 24.11 19:07». */
export const momentBadgeText = (plan: boolean, at: string) =>
  t(plan ? 'ожидаемый по плану на {time}' : 'по факту на {time}', { time: formatDateTime(at) })

/** Вид события словами: «Прицепка», «Отцепка» или «Смена порядка». */
export const changeKindText = (c: TrainChange) =>
  isSeq(c) ? t('Смена порядка') : isAttach(c) ? t('Прицепка') : t('Отцепка')

/**
 * Отклонение факта от отработанного плана словами: «по плану 10:00, Караганда»; без плана или без отклонения — пусто.
 */
export function deviationText(c: TrainChange): string {
  if (!c.planDt) return ''
  const parts: string[] = []
  if (c.planDt !== c.dt) parts.push(formatDateTime(c.planDt))
  if (c.planStation !== null && c.planStation !== c.station) parts.push(dictManager.getValue('Station', c.planStation))
  return parts.length ? t('по плану {what}', { what: parts.join(', ') }) : t('по плану')
}

/**
 * Отметка рейса в журнале поезда: отправление или прибытие. Не изменение состава — момент, на который можно посмотреть
 * состав. fact — время фактическое (прошлое), иначе плановое (настоящее).
 */
export interface JournalMark {
  key: string
  trip: TripRef
  kind: 'departure' | 'arrival'
  dt: string
  fact: boolean
}

/**
 * Отметки рейсов поезда: прошлое — фактические отправления и прибытия, настоящее — плановые отправления рейсов в
 * статусе «план» и плановые прибытия рейсов в статусе «план» и «выполняется». По времени.
 */
export function tripMarksOf(card: TrainCard, past: boolean): JournalMark[] {
  const res: JournalMark[] = []
  const add = (m: Omit<JournalMark, 'key'>) => res.push({ ...m, key: `${m.trip.id}-${m.kind}-${m.fact ? 'f' : 'p'}` })
  for (const r of card.tripMarks) {
    if (past) {
      if (r.departedDt) add({ trip: r.trip, kind: 'departure', dt: r.departedDt, fact: true })
      if (r.arrivedDt) add({ trip: r.trip, kind: 'arrival', dt: r.arrivedDt, fact: true })
    } else {
      if (r.tripStatusType === TripStatus.PLANNED)
        add({ trip: r.trip, kind: 'departure', dt: r.plannedDt, fact: false })
      if (r.tripStatusType === TripStatus.PLANNED || r.tripStatusType === TripStatus.IN_PROGRESS)
        add({ trip: r.trip, kind: 'arrival', dt: r.arrival, fact: false })
    }
  }
  return res.sort((a, b) => new Date(a.dt).getTime() - new Date(b.dt).getTime())
}

/**
 * Отметки одного рейса поезда: отправление и прибытие — фактические, если были, иначе плановые. По времени.
 */
export function tripMarksOfTrip(card: TrainCard, trip: number): JournalMark[] {
  const r = card.tripMarks.find((x) => x.trip.id === trip)
  if (!r) return []
  const mark = (kind: JournalMark['kind'], fact: string | null, plan: string): JournalMark => ({
    key: `${r.trip.id}-${kind}-${fact ? 'f' : 'p'}`,
    trip: r.trip,
    kind,
    dt: fact ?? plan,
    fact: fact !== null,
  })
  return [mark('departure', r.departedDt, r.plannedDt), mark('arrival', r.arrivedDt, r.arrival)]
}

/** Журнал поезда для показа: факты по времени, затем ожидающие планы по времени. */
export function journalOf(card: TrainCard): { facts: TrainChange[]; plans: TrainChange[] } {
  return {
    facts: card.changes.filter(isFact).sort(byJournal),
    plans: card.changes.filter(isWaiting).sort(byJournal),
  }
}

/**
 * Более поздние факты того же вагона среди известных фактов facts — от последнего: пока они есть, факт c не удалить
 * (сначала удаляются они). Факты других вагонов и смены порядка не мешают; смене порядка не мешают никакие факты.
 * Факты вагона в других поездах проверяет сервер.
 */
export function laterFactsOfWagon(facts: TrainChange[], c: TrainChange): TrainChange[] {
  const at = Date.parse(c.dt)
  const wagon = c.wagon?.id
  if (wagon === undefined) return []
  return facts
    .filter(
      (x) =>
        isFact(x) &&
        x.wagon?.id === wagon &&
        x.id !== c.id &&
        (Date.parse(x.dt) > at || (Date.parse(x.dt) === at && x.id > c.id)),
    )
    .sort((a, b) => byJournal(b, a))
}

/** Действие с записью журнала: текст на русском, почему недоступно (пусто — доступно). */
export interface ChangeAction {
  label: string
  reason: string
  run: () => void
}

/**
 * Действия с записью журнала — общие для экранов: у ожидающего плана «Редактировать» (если задан run.edit),
 * «Отметить выполнение» и «Отменить план», у факта «Редактировать» (основания) и «Удалить факт». Недоступное — с причиной (права на сервере: canMark, canCancel, canUndo; удалить факт нельзя, пока после него
 * есть факты того же вагона).
 *
 * @param facts известные факты поезда или рейса: по ним — более поздние факты того же вагона
 */
export function changeActions(
  c: TrainChange,
  facts: TrainChange[],
  run: {
    mark: (c: TrainChange) => void
    cancel: (c: TrainChange) => void
    undo: (c: TrainChange) => void
    /** исправить запись; нет — пункта «Редактировать» нет */
    edit?: (c: TrainChange) => void
  },
): ChangeAction[] {
  const editAction = (reason: string): ChangeAction[] =>
    run.edit ? [{ label: 'Редактировать', reason: c.canEdit ? '' : t(reason), run: () => run.edit!(c) }] : []
  if (isWaiting(c)) {
    return [
      ...editAction('Исправляет план планировщик вагонов или ЛНП рейса'),
      {
        label: 'Отметить выполнение',
        reason: c.canMark ? '' : t('Отмечает план планировщик вагонов, в рейсе — ЛНП рейса'),
        run: () => run.mark(c),
      },
      {
        label: 'Отменить план',
        reason: c.canCancel ? '' : t('Отменяет план планировщик вагонов или ЛНП рейса'),
        run: () => run.cancel(c),
      },
    ]
  }
  if (isFact(c)) {
    const reason = !c.canUndo
      ? t('Удаляет факт планировщик вагонов, в рейсе — ЛНП рейса')
      : laterFactsOfWagon(facts, c).length > 0
        ? t('Сначала удалите более позднюю прицепку или отцепку этого вагона')
        : ''
    return [
      ...editAction('Основания факта исправляет тот, кто может удалить факт'),
      // факт, который отработал план, — отметка выполнения плана: её отменяют; факт без плана удаляют
      { label: c.plan !== null ? 'Отменить отметку о выполнении' : 'Удалить факт', reason, run: () => run.undo(c) },
    ]
  }
  return []
}
