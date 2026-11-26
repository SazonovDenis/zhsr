import { ref } from 'vue'

import type { TrainCard, TrainChange, TrainWagon } from '@/api/trains'
import { attachWagons, cancelChangePlan, detachWagons, editTrainChange, seqWagons } from '@/api/trains'
import { openDialog } from '@/shared/components/dialog/openDialog'
import { isSeq, isWaiting } from '@/shared/trains/trainJournal'
import type { AttachChoice } from './attach'
import { changeBody, loadStopGroups } from './attach'
import AttachWagonsDialog from './AttachWagonsDialog.vue'
import ChangeEditDialog from './ChangeEditDialog.vue'
import ChangeUndoDialog from './ChangeUndoDialog.vue'
import { confirmCancelByWarnings } from './confirmByWarnings'
import DetachWagonsDialog from './DetachWagonsDialog.vue'
import MarkChangeDialog from './MarkChangeDialog.vue'
import TrainWagonSeqDialog from './TrainWagonSeqDialog.vue'

/**
 * Действия над составом поезда — одни и те же на экране поезда и на экране прицепки и отцепки в рейсе: прицепить,
 * отцепить вагоны (из меню вагона — с этим вагоном), сменить порядок вагонов, отметить план, отменить план, исправить запись журнала, удалить
 * ошибочный факт. Окна открываются здесь; запрос прицепки и отцепки выполняет окно: ошибка остаётся в окне. Проверки и
 * права — на сервере.
 *
 * @param o.card    поезд: на экране поезда — уже загруженный, в формировании рейса — загружается к действию
 * @param o.at      плановое время по умолчанию, ISO
 * @param o.trip    рейс, остановки которого предлагаются для плана первыми; null — вне рейса
 * @param o.inProgress у записи журнала рейс в статусе «выполняется»: отметка спрашивает ответственных
 * @param o.changed состав изменился: поезд после изменения (null — перечитать)
 * @param o.failed  ошибка запроса — показать на странице
 */
export function useTrainActions(o: {
  card: () => Promise<TrainCard>
  at: () => string
  trip: () => number | null
  inProgress: (c: TrainChange) => boolean
  changed: (card: TrainCard | null) => void | Promise<void>
  failed: (e: unknown) => void
}) {
  const running = ref(false)

  async function run(work: () => Promise<TrainCard | null>) {
    running.value = true
    try {
      const card = await work()
      if (card !== undefined) await o.changed(card)
    } catch (e) {
      o.failed(e)
    } finally {
      running.value = false
    }
  }

  /** Прицепить вагоны: окно выбора вагонов, места и позиции. */
  async function attach() {
    let card: TrainCard
    let groups
    try {
      card = await o.card()
      groups = await loadStopGroups(card)
    } catch (e) {
      o.failed(e)
      return
    }
    const done = await openDialog<TrainCard>(AttachWagonsDialog, {
      card,
      groups,
      station: card.station,
      at: o.at(),
      trip: o.trip(),
      submit: (choice: AttachChoice) => attachWagons(card.id, changeBody(choice.wagons, choice)),
    })
    if (done) await o.changed(done)
  }

  /**
   * Отцепить вагоны: окно выбора вагонов состава, места и времени. Из меню вагона — с этим вагоном, уже отмеченным.
   *
   * @param wagon       вагон из меню; null — кнопка «Отцепить вагоны»
   * @param factAllowed вагон в поезде по факту: окно открывается фактом; иначе — планом
   * @param plan        окно сразу планом
   * @param at          плановое время по умолчанию, ISO; null — как у экрана
   */
  async function detach(wagon: TrainWagon | null = null, factAllowed = true, plan = false, at: string | null = null) {
    let card: TrainCard
    let groups
    try {
      card = await o.card()
      groups = await loadStopGroups(card)
    } catch (e) {
      o.failed(e)
      return
    }
    const done = await openDialog<TrainCard>(DetachWagonsDialog, {
      card,
      groups,
      at: at ?? o.at(),
      trip: o.trip(),
      wagon: wagon?.wagon ?? null,
      plan: plan || !factAllowed,
      submit: (choice: AttachChoice) => detachWagons(card.id, changeBody(choice.wagons, choice)),
    })
    if (done) await o.changed(done)
  }

  /** Сменить порядок вагонов: окно с составом, местом и временем. */
  async function seq() {
    let card: TrainCard
    let groups
    try {
      card = await o.card()
      groups = await loadStopGroups(card)
    } catch (e) {
      o.failed(e)
      return
    }
    const done = await openDialog<TrainCard>(TrainWagonSeqDialog, {
      card,
      groups,
      at: o.at(),
      trip: o.trip(),
      submit: (choice: AttachChoice) => seqWagons(card.id, changeBody(choice.wagons, choice)),
    })
    if (done) await o.changed(done)
  }

  /** Отметить исполнение плана. */
  async function mark(c: TrainChange) {
    const done = await openDialog<TrainCard>(MarkChangeDialog, { change: c, inProgress: o.inProgress(c) })
    if (done) await o.changed(done)
  }

  /** Отменить план: без предупреждений — сразу, без окна; с предупреждениями — после подтверждения. */
  async function cancel(c: TrainChange) {
    running.value = true
    const ok = await confirmCancelByWarnings(c)
    running.value = false
    if (ok) await run(() => cancelChangePlan(c))
  }

  /** Исправить запись журнала: план — целиком, факт — основания. */
  async function edit(c: TrainChange) {
    let card: TrainCard
    let groups
    try {
      card = await o.card()
      groups = isWaiting(c) ? await loadStopGroups(card) : []
    } catch (e) {
      o.failed(e)
      return
    }
    const done =
      isSeq(c) && isWaiting(c)
        ? await openDialog<TrainCard>(TrainWagonSeqDialog, {
            card,
            groups,
            at: c.dt,
            change: c,
            submit: (choice: AttachChoice) => editTrainChange(c, changeBody(choice.wagons, choice)),
          })
        : await openDialog<TrainCard>(ChangeEditDialog, { card, change: c, groups })
    if (done) await o.changed(done)
  }

  /** Удалить ошибочный факт — с подтверждением. */
  async function undo(c: TrainChange) {
    const done = await openDialog<true>(ChangeUndoDialog, { change: c })
    if (done) await o.changed(null)
  }

  return { running, attach, detach, seq, mark, cancel, undo, edit }
}
