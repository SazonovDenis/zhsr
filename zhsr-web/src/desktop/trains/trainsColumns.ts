import type { TrainRow } from '@/api/trains'
import { defineColumns } from '@/shared/components/table/types'
import CellTrainName from './cells/CellTrainName.vue'
import CellTrainTrip from './cells/CellTrainTrip.vue'
import { trainPlaceText } from './trainPlace'

/** Колонки таблицы «Поезда». Справочники Station, Depot, Yard, LocationKind и TripState готовит страница. */
export const trainsColumns = defineColumns<TrainRow>([
  // под обозначением — количество вагонов; колонки «Вагоны» нет, её место отдано соседним колонкам
  { field: 'name', title: 'Поезд', width: 'minmax(9.3rem, 0.9fr)', cell: CellTrainName },
  // как расположение в списке вагонов
  {
    field: 'station',
    title: 'Где вагоны',
    width: 'minmax(10.5rem, 1fr)',
    format: (_, row) => trainPlaceText(row),
  },
  // выполняющийся рейс, а если его нет — ближайший в статусе «план»; статус — плашкой
  { field: 'trip', title: 'Ближайший рейс', width: 'minmax(17.5rem, 1.8fr)', cell: CellTrainTrip },
])
