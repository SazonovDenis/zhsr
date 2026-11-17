import type { TrainRow } from '@/api/trains'
import { t } from '@/boot/i18n'
import { defineColumns } from '@/shared/components/table/types'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import { trainPlaceText } from './trainPlace'

/** Колонки таблицы «Поезда». Справочники Station, Depot, Yard и LocationKind готовит страница. */
export const trainsColumns = defineColumns<TrainRow>([
  { field: 'name', title: 'Поезд', width: 'minmax(10rem, 1fr)' },
  {
    field: 'wagons',
    title: 'Вагоны',
    // количество: «23 вагона»; сами вагоны — в панели
    width: '8rem',
    format: (_, row) => (row.wagons.length ? t('{n} вагонов', row.wagons.length) : t('нет вагонов')),
  },
  // как расположение в списке вагонов
  { field: 'station', title: 'Где вагоны', format: (_, row) => trainPlaceText(row) },
  {
    field: 'trip',
    title: 'Рейс',
    width: 'minmax(14rem, 1.5fr)',
    format: (_, row) =>
      row.trip
        ? t('выполняется: {trip}', { trip: tripRefDatedText(row.trip) })
        : row.nextTrip
          ? t('ближайший: {trip}', { trip: tripRefDatedText(row.nextTrip) })
          : '',
  },
])
