package kz.zhsr.app.dto.route;

/**
 * Остановка маршрута в редакторе.
 *
 * @param station      станция (station)
 * @param km           расстояние от начальной станции маршрута, км
 * @param runDuration  время в пути от предыдущей остановки, мин
 * @param stopDuration стоянка, мин
 */
public record RouteStop(long station, int km, int runDuration, int stopDuration) {
}
