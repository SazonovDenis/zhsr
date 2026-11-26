package kz.zhsr.app.service;

/**
 * SQL расчёта неготовности вагонов ({@link WagonProblems}).
 */
final class WagonProblemSql {

    private WagonProblemSql() {
    }

    /**
     * Рейсы поездов в статусах states с началом, концом (фактическим или плановым), станциями отправления и прибытия,
     * по плановому отправлению. Параметры trains, states.
     */
    static String trips() {
        return """
                select trip.id, trip.train, trip.route, trip.trip_status_type, coalesce(trip.departed_dt, trip.planned_dt) as beg,
                       coalesce(trip.arrived_dt, trip.planned_dt + make_interval(mins => %s)) as fin,
                       (select route_station.station from route_station
                        where route_station.route = route.id and route_station.have_stop
                        order by route_station.seq
                        limit 1) as departure_station,
                       (select route_station.station from route_station
                        where route_station.route = route.id and route_station.have_stop
                        order by route_station.seq desc
                        limit 1) as arrival_station
                from trip
                join route on route.id = trip.route
                where trip.train = any(:trains) and trip.trip_status_type = any(:states)
                order by trip.planned_dt, route.train_number
                """.formatted(RouteSql.DURATION);
    }

    /** Время последней прицепки фактом каждого вагона к каждому поезду. Параметры wagons, fact, attach. */
    static String lastAttaches() {
        return """
                select train_wagon_change.wagon, train_wagon_change.train, max(train_wagon_change.dt) as dt
                from train_wagon_change
                where train_wagon_change.wagon = any(:wagons) and train_wagon_change.kind = :fact
                  and train_wagon_change.change_type = :attach
                group by train_wagon_change.wagon, train_wagon_change.train
                """;
    }

}
