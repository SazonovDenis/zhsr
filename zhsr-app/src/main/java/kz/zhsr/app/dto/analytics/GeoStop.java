package kz.zhsr.app.dto.analytics;

/**
 * Остановка маршрута на карте.
 *
 * @param id           остановка (route_station.id)
 * @param station      станция остановки - id справочника Station
 * @param haveStop     поезд здесь останавливается; false - станция следования
 * @param km           километр линии маршрута
 * @param arrivalMin   прибытие по расписанию, минуты от отправления с начальной станции
 * @param departureMin отправление по расписанию, там же
 */
public record GeoStop(long id, long station, boolean haveStop, double km, double arrivalMin, double departureMin) {
}
