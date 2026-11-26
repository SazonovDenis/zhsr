package kz.zhsr.app.service.analytics;

import kz.zhsr.app.service.RouteSql;

/**
 * SQL {@link GeoService}. Параметры запросов - в описании каждого метода.
 */
final class GeoSql {

    private GeoSql() {
    }

    /** Поля рейса карты: id, route, train, trip_status_type, planned_dt, departed_dt, arrived_dt, arrival. */
    private static final String TRIP_FIELDS = """
            select trip.id, trip.route, trip.train, trip.trip_status_type, trip.planned_dt, trip.departed_dt,
                   trip.arrived_dt, trip.planned_dt + make_interval(mins => %s) as arrival
            from trip
            join route on route.id = trip.route
            """.formatted(RouteSql.DURATION);

    /**
     * Рейсы с поездом, которые шли в период, и неотправленные с плановым отправлением в периоде - по отправлению.
     * Параметры: from, to - период; planned - состояние «план».
     */
    static String periodTrips() {
        return TRIP_FIELDS + """
                where trip.train is not null
                  and (trip.departed_dt < :to and (trip.arrived_dt is null or trip.arrived_dt > :from)
                       or trip.departed_dt is null and trip.trip_status_type = :planned
                          and trip.planned_dt >= :from and trip.planned_dt < :to)
                order by coalesce(trip.departed_dt, trip.planned_dt), trip.id""";
    }

    /** Рейсы поездов по отправлению. Параметры: trains - поезда, states - состояния рейсов. */
    static String trainTrips() {
        return TRIP_FIELDS + """
                where trip.train = any(:trains) and trip.trip_status_type = any(:states)
                order by coalesce(trip.departed_dt, trip.planned_dt), trip.id""";
    }

    /**
     * Положения поездов рейсов от отправления до прибытия: trip, dt, x, y - по рейсу и времени. Параметры: trips -
     * отправленные рейсы, to - конец периода.
     */
    static String tripPoints() {
        return """
                select trip.id as trip, train_geo_location.dt, train_geo_location.x, train_geo_location.y
                from trip
                join train_geo_location on train_geo_location.train = trip.train
                     and train_geo_location.dt >= trip.departed_dt
                     and train_geo_location.dt <= least(trip.arrived_dt, :to)
                where trip.id = any(:trips)
                order by trip.id, train_geo_location.dt""";
    }

    /** Маршруты по id. Параметр routes. */
    static String routesById() {
        return """
                select id, train_number, name
                from route
                where id = any(:routes)
                order by train_number""";
    }

    /** Маршруты, действующие в периоде, и маршруты рейсов. Параметры: from, to - даты, routes - маршруты рейсов. */
    static String routes() {
        return """
                select id, train_number, name
                from route
                where (dbeg <= :to and (dend is null or dend >= :from)) or id = any(:routes)
                order by train_number""";
    }
}
