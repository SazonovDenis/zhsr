package kz.zhsr.app.service;

/**
 * SQL маршрутов, общий для запросов разных сервисов.
 */
public final class RouteSql {

    /**
     * Время в пути маршрута route, мин: прибытие на конечную станцию минус отправление с начальной (route_station,
     * минуты от полуночи); у маршрута без станций 0. Выражение для select, where и order by; маршрут - таблица route
     * запроса.
     */
    public static final String DURATION = """
            (select coalesce(max(route_station.arrival_min) - min(route_station.departure_min), 0)
             from route_station where route_station.route = route.id)""";

    private RouteSql() {
    }
}
