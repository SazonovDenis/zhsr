import { shallowRef, watch } from 'vue'

import type { MapPoint } from '@/api/stages'
import { fetchStageLines, fetchStationPoints } from '@/api/stages'
import type { PointMark } from '@/shared/components/map/mapMarks'
import { dictManager } from '@/shared/dict/DictManager'
import { useLoad } from '@/shared/loadable'

/** Таблица администрирования с картой: справа от списка и в диалоге записи. */
export interface AdminMapTable {
  /** поле с названием записи: подпись записи на карте; null — на карте не записи, а их станции */
  name: string | null
  /** поля-ссылки на станции записи; пусто — запись сама станция, её координаты — поля x и y */
  stations: string[]
  /** разделы формы: заголовок и поля; поле вне разделов — в первом */
  sections: { title: string; fields: string[] }[]
}

export const ADMIN_MAPS: Record<string, AdminMapTable> = {
  station: {
    name: 'name',
    stations: [],
    sections: [
      { title: 'Станция', fields: [] },
      { title: 'Расположение', fields: ['x', 'y'] },
    ],
  },
  depot: {
    name: 'name',
    stations: ['station'],
    sections: [
      { title: 'Депо', fields: [] },
      { title: 'Расположение', fields: ['station'] },
    ],
  },
  yard: {
    name: 'name',
    stations: ['station'],
    sections: [
      { title: 'Парк отстоя', fields: [] },
      { title: 'Расположение', fields: ['station'] },
    ],
  },
  stage: {
    name: null,
    stations: ['from_station', 'to_station'],
    sections: [{ title: 'Перегон', fields: [] }],
  },
}

/**
 * Станции с точками, по названию: порядок важности при прореживании.
 *
 * @param refresh названия — из обновлённого справочника: станции изменились
 */
export async function fetchStationMarks(refresh = false): Promise<PointMark[]> {
  const [stations] = await Promise.all([
    fetchStationPoints(),
    refresh ? dictManager.refreshDict('Station') : dictManager.prepareDict('Station'),
  ])
  return stations
    .map((s) => ({ id: s.station, point: s.point, text: dictManager.getValue('Station', s.station) }))
    .sort((a, b) => a.text.localeCompare(b.text))
}

/**
 * Линия между парой станций: линия их перегона; перегона или его точек нет — прямая; точки одной из станций нет —
 * линии нет.
 *
 * @param pair   станции; null — пары нет
 * @param points точка станции; null — нет
 */
export function useStationLine(pair: () => [number, number] | null, points: (station: number) => MapPoint | null) {
  const line = shallowRef<MapPoint[] | null>(null)
  const key = () => pair()?.join(',') ?? ''

  const load = useLoad(async () => {
    // пару сменили, пока шла загрузка, — загрузка повторяется по новой
    let loadedKey: string
    let exact: MapPoint[]
    do {
      loadedKey = key()
      const stations = pair()
      const lines = stations ? (await fetchStageLines(stations)).lines : []
      // линий несколько — перегона между станциями нет, это путь через другие станции
      exact = lines.length === 1 ? lines[0]!.points : []
    } while (loadedKey !== key())
    const stations = pair()
    if (!stations) return
    const from = points(stations[0])
    const to = points(stations[1])
    line.value = exact.length > 1 ? exact : from && to ? [from, to] : null
  })

  function update() {
    line.value = null
    if (pair()) void load.run()
  }

  watch(key, update, { immediate: true })
  return { line, load, update }
}
