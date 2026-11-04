package kz.zhsr.app.dto;

/**
 * Станция маршрута (route_station).
 *
 * @param station      станция (station)
 * @param km           расстояние от начальной станции маршрута, км
 * @param haveStop     остановка (have_stop); false - станция следования, поезд проходит её без остановки
 * @param runDuration  время в пути от предыдущей станции маршрута, мин; у начальной станции 0
 * @param stopDuration стоянка, мин
 */
public record RoutePoint(long station, int km, boolean haveStop, int runDuration, int stopDuration) {
}
