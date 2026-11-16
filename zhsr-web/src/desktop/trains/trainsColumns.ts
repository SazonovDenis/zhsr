import type { TrainRow } from '@/api/trains'
import { t } from '@/boot/i18n'
import { defineColumns } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { tripRefDatedText } from '@/shared/trips/tripRefText'

/** Колонки таблицы «Поезда». Станции — справочник Station: его готовит страница. */
export const trainsColumns = defineColumns<TrainRow>([
  { field: 'name', title: 'Поезд', width: 'minmax(10rem, 1fr)' },
  {
    field: 'wagons',
    title: 'Вагоны',
    width: 'minmax(14rem, 2fr)',
    cellClass: 'zs-mono',
    format: (_, row) => (row.wagons.length ? `${row.wagons.length}: ${row.wagons.join(', ')}` : t('нет вагонов')),
  },
  {
    field: 'station',
    title: 'Где вагоны',
    format: (_, row) =>
      row.trip
        ? t('в рейсе')
        : row.station
          ? dictManager.getValue('Station', row.station)
          : row.wagons.length
            ? t('на разных станциях')
            : '',
  },
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
  {
    field: 'plannedCount',
    title: 'По плану',
    align: 'right',
    format: (_, row) => (row.plannedCount ? String(row.plannedCount) : ''),
  },
  { field: 'dend', title: 'Расформирован', format: (_, row) => (row.dend ? formatDateTime(row.dend) : '') },
])
