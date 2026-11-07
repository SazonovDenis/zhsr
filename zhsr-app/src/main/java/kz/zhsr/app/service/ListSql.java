package kz.zhsr.app.service;

import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.route.RouteFilter;
import kz.zhsr.app.dto.trip.TripFilter;
import kz.zhsr.app.dto.wagon.WagonFilter;

import java.util.Map;

/**
 * SQL списков {@link ListService}: текст запроса, условия фильтра и видимости, сортировка. Логики здесь нет - только
 * сборка запроса по уже проверенному фильтру.
 * <p>
 * Пустое поле фильтра (null, пустая строка, пустой список) условия не добавляет. Алиасов нет; алиас - только у
 * выражения.
 */
final class ListSql {

    private ListSql() {
    }

    // --- вагоны

    /**
     * Поле строки -> выражение сортировки.
     */
    private static final Map<String, String> WAGON_SORT = Map.of(
            "number", "wagon.number",
            "dbegLocation", "wagon_info.dbeg_location",
            "dbegStatus", "wagon_info.dbeg_status"
    );

    /**
     * Вагоны. Расположение: location_type и place - место по виду расположения (станция, депо, парк отстоя или рейс);
     * у вагона в рейсе - trip.
     */
    static SqlQuery wagons(WagonFilter filter, Visibility.Access access, ListSort sort) {
        SqlWhere where = new SqlWhere();
        where.visible(
                access.byDepot() ? (access.depot() == null ? "true" : "wagon.depot = :visDepot") : null,
                access.allTripWagons()
                        ? "wagon.id in (select trip_wagon.wagon from trip_wagon where trip_wagon.trip = :visTrip)"
                        : null,
                access.wagons().isEmpty() ? null : "wagon.id = any(:visWagons)",
                access);
        if (filter.number() != null && !filter.number().isBlank()) {
            where.add("strpos(wagon.number, :number) > 0", "number", filter.number().trim());
        }
        if (filter.station() != null) {
            where.add("""
                    (wagon_info.station = :station
                     or wagon_info.depot in (select depot.id from depot where depot.station = :station)
                     or wagon_info.yard in (select yard.id from yard where yard.station = :station))""",
                    "station", filter.station());
        }
        if (filter.trip() != null) {
            where.add("wagon_info.trip = :trip", "trip", filter.trip());
        }
        where.any("wagon_info.location_type", "locationKinds", filter.locationKinds());
        where.any("wagon_info.wagon_status_type", "states", filter.states());

        return where.query("""
                select wagon.id, wagon.number, wagon.wagon_type, wagon.model_type, wagon.depot,
                       wagon_info.location_type,
                       coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard, wagon_info.trip) as place,
                       wagon_info.trip, wagon_info.dbeg_location, wagon_info.wagon_status_type,
                       wagon_info.dbeg_status
                from wagon
                join wagon_info on wagon_info.wagon = wagon.id
                """, sort.orderBy(WAGON_SORT, "wagon.number, wagon.id"));
    }

    // --- рейсы

    private static final Map<String, String> TRIP_SORT = Map.of(
            "trainNumber", "route.train_number",
            "duration", "route.duration",
            "plannedDt", "trip.planned_dt"
    );

    static SqlQuery trips(TripFilter filter, Visibility.Access access, ListSort sort) {
        SqlWhere where = new SqlWhere();
        where.visible(
                access.byDepot() ? (access.depot() == null ? "true" : routeViaDepot("trip.route")) : null,
                access.trip() == null ? null : "trip.id = any(:visTrips)",
                null,
                access);
        if (filter.id() != null) {
            where.add("trip.id = :id", "id", filter.id());
        }
        if (filter.route() != null) {
            where.add("trip.route = :route", "route", filter.route());
        }
        if (filter.station() != null) {
            where.add("""
                    exists (select 1 from route_station
                            where route_station.route = trip.route and route_station.station = :station)""",
                    "station", filter.station());
        }
        if (filter.from() != null) {
            // рейс, идущий в периоде, виден и если отправился раньше from
            where.add("(trip.trip_status_type = " + TripStatus.IN_PROGRESS
                    + " or trip.planned_dt + make_interval(mins => route.duration) >= :from)", "from", filter.from());
        }
        if (filter.to() != null) {
            where.add("trip.planned_dt < :to", "to", filter.to());
        }
        where.any("trip.trip_status_type", "states", filter.states());

        return where.query("""
                select trip.id, route.train_number, trip.route, route.name, route.transport_type, route.route_kind,
                       route.duration, trip.trip_status_type, trip.planned_dt, trip.departed_dt, trip.arrived_dt,
                       trip.departure_marked_dt, trip.arrival_marked_dt
                from trip
                join route on route.id = trip.route
                """, sort.orderBy(TRIP_SORT, "trip.planned_dt, trip.id"));
    }

    // --- маршруты

    private static final Map<String, String> ROUTE_SORT = Map.of(
            "trainNumber", "route.train_number",
            "duration", "route.duration"
    );

    /**
     * @param active true - только действующие (dend пуст или не раньше сегодняшнего дня), false - только завершённые,
     *               null - все
     */
    static SqlQuery routes(RouteFilter filter, Boolean active, Visibility.Access access, ListSort sort) {
        SqlWhere where = new SqlWhere();
        where.visible(
                access.byDepot() ? (access.depot() == null ? "true" : routeViaDepot("route.id")) : null,
                access.trip() == null ? null : "route.id = (select trip.route from trip where trip.id = :visTrip)",
                null,
                access);
        where.any("route.route_kind", "kinds", filter.kinds());
        if (Boolean.TRUE.equals(active)) {
            where.add("(route.dend is null or route.dend >= current_date)");
        } else if (Boolean.FALSE.equals(active)) {
            where.add("route.dend < current_date");
        }

        return where.query("""
                select route.id, route.train_number, route.name, route.route_kind, route.transport_type, route.dbeg,
                       route.dend, route.duration, route.conductor_hours, route.lnp_hours, route.pem_hours
                from route
                """, sort.orderBy(ROUTE_SORT, "route.train_number, route.id"));
    }

    /**
     * Маршрут route идёт через станцию депо :visDepot.
     */
    private static String routeViaDepot(String route) {
        return """
                exists (select 1 from route_station join depot on depot.station = route_station.station
                        where route_station.route = %s and depot.id = :visDepot)""".formatted(route);
    }

    /**
     * Условия WHERE и их параметры.
     */

}
