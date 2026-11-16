package kz.zhsr.app.dto.route;

/**
 * Остановка маршрута в редакторе.
 *
 * @param station      станция (station)
 * @param km           расстояние от начальной станции маршрута, км
 * @param arrivalMin   прибытие: минуты от полуночи дня отправления с начальной станции; null - у начальной станции
 * @param departureMin отправление: минуты от полуночи дня отправления с начальной станции; null - у конечной станции
 */
public record RouteStop(long station, int km, Integer arrivalMin, Integer departureMin) {
}
