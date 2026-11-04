import { http } from './http'

/** API перегонов на карте: точки станций и линии перегонов (stage, stage_point). */

/** Точка линии перегона на карте (stage_point). */
export interface MapPoint {
  /** id точки (stage_point) */
  id: number
  /** долгота (WGS 84) */
  x: number
  /** широта (WGS 84) */
  y: number
}

/** Станция на карте: точка станции (station.stage_point). */
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
  /** точки линии по порядку от fromStation к toStation; первая и последняя — сами станции */
  points: MapPoint[]
}

/** Рисунок пути на карте. */
export interface StageLines {
  /** станции, у которых есть точка на карте; у станции без перегонов записи нет */
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
