import type { TrainChange } from '@/api/trains'

/**
 * Что выбрано на экране поезда — для карты: «сейчас», запись журнала или рейс на отправление (end = false) либо прибытие.
 * at — момент, ISO: у плана — когда он действует.
 */
export type TrainMapView =
  | { kind: 'now' }
  | { kind: 'change'; change: TrainChange; at: string }
  | { kind: 'trip'; trip: number; end: boolean; at: string }
