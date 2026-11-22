import type { InjectionKey } from 'vue'

import type { WagonRow } from '@/api/wagons'
import type { WagonFit } from '../trips/wagonPick'

/** Как вагон подходит для прицепки: к причинам выбора добавлена занятость в поезде. */
export interface AttachFit extends WagonFit {
  /** вагон занят в поезде на момент прицепки (переведено): «Занят: поезд …» или «Уже в поезде»; пусто — свободен */
  busy: string
}

/** Состояние выбора вагонов окна «Прицепить вагоны» для ячеек таблицы (provide/inject). */
export interface AttachPick {
  isPicked: (row: WagonRow) => boolean
  toggle: (row: WagonRow) => void
  fit: (row: WagonRow) => AttachFit
}

export const ATTACH_PICK: InjectionKey<AttachPick> = Symbol('attachPick')
