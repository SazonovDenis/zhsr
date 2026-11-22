import type { TrainCard, TrainChange, TrainState, TrainWagon } from '@/api/trains'
import { t } from '@/boot/i18n'
import { TrainWagonChangeType, TrainWagonKind, TrainWagonPlanStatus } from '@/shared/constants/TrainWagonChange'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'

/**
 * Журнал поезда и состав на момент (техника «журнал и состояние», doc/db-doc/journal-state-plan-fact.md) — общее для
 * экранов поезда, рейса и телефона. Журнал — записи train_wagon_change: факты и ожидающие планы. Состав — записи
 * train_wagon: слой «факт» — история, слой «план» — ожидаемый состав. Справочник Station готовит страница.
 */

export const isFact = (c: TrainChange) => c.kind === TrainWagonKind.FACT
export const isWaiting = (c: TrainChange) =>
  c.kind === TrainWagonKind.PLAN && c.planStatus === TrainWagonPlanStatus.WAITING
export const isAttach = (c: TrainChange) => c.changeType === TrainWagonChangeType.ATTACH

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
  const fact = isFact(c)
  const kind = fact ? TrainWagonKind.FACT : TrainWagonKind.PLAN
  const momentOf = (x: TrainChange) => (fact ? x.dt : planMoment(card, x))
  const at = new Date(momentOf(c)).getTime()
  const journal = journalOf(card)
  const same = (fact ? journal.facts : journal.plans).filter((x) => new Date(momentOf(x)).getTime() === at)
  const order = compositionAt(card, kind, new Date(at - 1).toISOString()).map((m) => m.wagon)
  const wagons = new Map(card.trainWagons.map((w) => [w.wagon, w]))
  for (const x of same.slice(0, same.findIndex((y) => y.id === c.id) + 1)) {
    const index = order.findIndex((w) => w.wagon === x.wagon.id)
    if (isAttach(x)) {
      const wagon = wagons.get(x.wagon.id)
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

/** Вид события словами: «Прицепка» или «Отцепка». */
export const changeKindText = (c: TrainChange) => (isAttach(c) ? t('Прицепка') : t('Отцепка'))

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

/** Журнал поезда для показа: факты по времени, затем ожидающие планы по времени. */
export function journalOf(card: TrainCard): { facts: TrainChange[]; plans: TrainChange[] } {
  const byTime = (a: TrainChange, b: TrainChange) => new Date(a.dt).getTime() - new Date(b.dt).getTime() || a.id - b.id
  return {
    facts: card.changes.filter(isFact).sort(byTime),
    plans: card.changes.filter(isWaiting).sort(byTime),
  }
}

/**
 * Более поздние факты того же вагона среди известных фактов facts — от последнего: пока они есть, факт c не удалить
 * (сначала удаляются они). Факты других вагонов не мешают. Факты вагона в других поездах проверяет сервер.
 */
export function laterFactsOfWagon(facts: TrainChange[], c: TrainChange): TrainChange[] {
  const at = Date.parse(c.dt)
  return facts
    .filter(
      (x) =>
        isFact(x) &&
        x.wagon.id === c.wagon.id &&
        x.id !== c.id &&
        (Date.parse(x.dt) > at || (Date.parse(x.dt) === at && x.id > c.id)),
    )
    .sort((a, b) => Date.parse(b.dt) - Date.parse(a.dt) || b.id - a.id)
}

/** Действие с записью журнала: текст на русском, почему недоступно (пусто — доступно). */
export interface ChangeAction {
  label: string
  reason: string
  run: () => void
}

/**
 * Действия с записью журнала — общие для экранов: у ожидающего плана «Отметить» и «Отменить план», у факта «Удалить
 * факт». Недоступное — с причиной (права на сервере: canMark, canCancel, canUndo; удалить факт нельзя, пока после него
 * есть факты того же вагона).
 *
 * @param facts известные факты поезда или рейса: по ним — более поздние факты того же вагона
 */
export function changeActions(
  c: TrainChange,
  facts: TrainChange[],
  run: { mark: (c: TrainChange) => void; cancel: (c: TrainChange) => void; undo: (c: TrainChange) => void },
): ChangeAction[] {
  if (isWaiting(c)) {
    return [
      {
        label: 'Отметить',
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
    return [{ label: 'Удалить факт', reason, run: () => run.undo(c) }]
  }
  return []
}
