package kz.zhsr.app.dto.analytics;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Диаграмма «Текущие рейсы». Время точек - минуты от from.
 *
 * @param from   начало периода
 * @param routes маршруты, действующие в периоде, и маршруты рейсов
 * @param trips  отправленные рейсы периода по порядку отправления
 * @param wagons положения вагонов вне рейсов; без точек (points = false) - пустой список
 * @param usrs   положения проводников вне рейсов; без точек - пустой список
 */
public record GeoChart(OffsetDateTime from, List<GeoRoute> routes, List<GeoTrip> trips, List<GeoWagon> wagons,
                       List<GeoUsr> usrs) {
}
