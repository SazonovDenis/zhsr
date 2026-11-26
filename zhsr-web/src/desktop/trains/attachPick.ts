import type { InjectionKey } from 'vue'

import type { WagonRow } from '@/api/wagons'
import type { Phrase } from '@/shared/trains/phrase'
import type { WagonFit } from '../trips/wagonPick'

/** Как вагон подходит для прицепки: к причинам выбора добавлена занятость в поезде. */
export interface AttachFit extends WagonFit {
  /** «Уже в поезде» (переведено) — вагон не выбирается; пусто — можно */
  busy: string
  /** предупреждения вагона фразами без номера */
  notes: Phrase[]
}

/** Состояние выбора вагонов окна «Прицепить вагоны» для ячеек таблицы (provide/inject). */
export interface AttachPick {
  isPicked: (row: WagonRow) => boolean
  toggle: (row: WagonRow) => void
  fit: (row: WagonRow) => AttachFit
}

export const ATTACH_PICK: InjectionKey<AttachPick> = Symbol('attachPick')
