import type { TripOption } from '@/api/trips'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'

/**
 * Рейс как значение CbList для строки TripOptionView: текст — номер поезда и маршрут, поиск — ещё и по остановкам
 * маршрута, в data — сам рейс. withTime — в тексте ещё плановое отправление: им выбранный рейс отличается от других
 * рейсов того же маршрута.
 */
export function tripOptionItem(trip: TripOption, withTime = false): ChoiceItem {
  const time = withTime ? ` • ${formatDateTime(trip.plannedDt)}` : ''
  return {
    id: trip.id,
    text: `№ ${trip.trainNumber} • ${trip.name}${time}`,
    search: trip.stops.map((station) => dictManager.getValue('Station', station)).join(' '),
    data: { trip },
  }
}

/** Рейс из значения, собранного tripOptionItem. */
export function tripOptionOf(item: ChoiceItem): TripOption {
  return item.data!.trip as TripOption
}
