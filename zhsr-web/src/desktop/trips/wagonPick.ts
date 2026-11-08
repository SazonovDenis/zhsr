import type { InjectionKey } from 'vue'

import type { WagonRow } from '@/api/wagons'

/** Как вагон подходит для добавления в состав рейса. */
export interface WagonFit {
  /** почему добавить нельзя (переведено): вагон в другом рейсе «план» или «выполняется», уже в составе; пусто — можно */
  block: string
  /** предупреждение (переведено): на момент отправления вагон не на станции отправления; добавить можно */
  warn: string
}

/** Состояние выбора вагонов окна «Добавить вагоны» для ячеек таблицы (provide/inject). */
export interface WagonPick {
  isPicked: (row: WagonRow) => boolean
  toggle: (row: WagonRow) => void
  fit: (row: WagonRow) => WagonFit
}

export const WAGON_PICK: InjectionKey<WagonPick> = Symbol('wagonPick')
