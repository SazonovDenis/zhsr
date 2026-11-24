import { http } from './http'

/** API перегонов на карте: точки станций и линии перегонов (stage, stage_point, station_point). */

/** Точка на карте: линии перегона (stage_point) или станции (station_point). */
export interface MapPoint {
  /** долгота (WGS 84) */
  x: number
  /** широта (WGS 84) */
  y: number
}

/** Станция на карте: точка станции (station_point). */
export interface StationPoint {
  /** станция — id справочника Station */
  station: number
  point: MapPoint
}

/**
 * Линия перегона на пути между соседними станциями списка. Соседние станции соединены перегоном напрямую — одна
 * линия; иначе — линии перегонов кратчайшего пути между ними, по порядку.
 */
export interface StageLine {
  /** номер пары соседних станций: линия лежит на пути от stations[index] к stations[index + 1] */
  index: number
  /** перегон (stage) */
  stage: number
  /** станция перегона, от которой идёт линия по ходу пути — id справочника Station */
  fromStation: number
  /** станция перегона, к которой идёт линия — id справочника Station */
  toStation: number
  /**
   * точки линии по порядку от fromStation к toStation; первая и последняя — сами станции. Точек у перегона нет —
   * прямая между станциями; точки станции тоже нет — часть прямой между ближайшими станциями пути с точками; таких
   * станций с обеих сторон нет — пусто
   */
  points: MapPoint[]
}

/** Рисунок пути на карте. */
export interface StageLines {
  /** станции, у которых есть точка на карте; у станции без точки записи нет */
  stations: StationPoint[]
  /** линии перегонов по порядку станций; у пары соседних станций без пути по перегонам между ними линий нет */
  lines: StageLine[]
}

/**
 * Рисунок пути по станциям, идущим по порядку: точки станций и линии перегонов между соседними станциями. Между
 * соседними станциями без общего перегона сервер строит кратчайший путь по перегонам.
 */
export async function fetchStageLines(stations: number[]): Promise<StageLines> {
  return (await http.get<StageLines>('/stages/lines', { params: { stations } })).data
}

/** Точки всех станций; у станции без точки записи нет. */
export async function fetchStationPoints(): Promise<StationPoint[]> {
  return (await http.get<StationPoint[]>('/stages/stations')).data
}
