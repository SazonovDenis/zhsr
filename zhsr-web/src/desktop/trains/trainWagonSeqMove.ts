import type { InjectionKey } from 'vue'

/** Перестановка вагона кнопками строки окна «Сменить порядок» (provide/inject). */
export interface TrainWagonSeqMove {
  /** на одну позицию: step -1 — вверх, 1 — вниз */
  move: (wagon: number, step: -1 | 1) => void
  /** сдвинуть в эту сторону можно */
  canMove: (wagon: number, step: -1 | 1) => boolean
}

export const TRAIN_WAGON_SEQ_MOVE: InjectionKey<TrainWagonSeqMove> = Symbol('trainWagonSeqMove')
