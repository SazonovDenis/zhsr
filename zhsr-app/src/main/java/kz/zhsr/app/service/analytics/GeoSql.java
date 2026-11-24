package kz.zhsr.app.service.analytics;

/**
 * SQL {@link GeoService}. Параметры запросов - в описании каждого метода.
 */
final class GeoSql {

    private GeoSql() {
    }

    /**
     * Отправленные рейсы с поездом, которые шли в период: id, route, train, planned_dt, departed_dt, arrived_dt - по
     * отправлению. Параметры: from, to - период; usr - сотрудник бригады (только при own).
     */
    static String trips(boolean own) {
        return """
                select trip.id, trip.route, trip.train, trip.planned_dt, trip.departed_dt, trip.arrived_dt
                from trip
                where trip.departed_dt < :to
                  and (trip.arrived_dt is null or trip.arrived_dt > :from)
                  and trip.train is not null"""
                + (own ? " and trip.id in (select trip_crew.trip from trip_crew where trip_crew.usr = :usr)" : "")
                + " order by trip.departed_dt, trip.id";
    }

    /**
     * Положения вагонов рейсов, пока вагон в поезде рейса: trip, wagon, dt, x, y - по рейсу, вагону и времени.
     * Параметры: trips - рейсы, fact - слой «факт» участия, to - конец периода.
     */
    static String tripWagonPoints() {
        return """
                select trip.id as trip, wagon_geo_location.wagon, wagon_geo_location.dt, wagon_geo_location.x,
                       wagon_geo_location.y
                from trip
                join train_wagon on train_wagon.train = trip.train and train_wagon.kind = :fact
                     and (train_wagon.dend is null or train_wagon.dend > trip.departed_dt)
                     and (trip.arrived_dt is null or train_wagon.dbeg < trip.arrived_dt)
                join wagon_geo_location on wagon_geo_location.wagon = train_wagon.wagon
                     and wagon_geo_location.dt >= greatest(trip.departed_dt, train_wagon.dbeg)
                     and wagon_geo_location.dt <= least(trip.arrived_dt, train_wagon.dend, :to)
                where trip.id = any(:trips)
                order by trip.id, wagon_geo_location.wagon, wagon_geo_location.dt""";
    }

    /** Положения вагонов за период: wagon, dt, x, y - по вагону и времени. Параметры: from, to - период. */
    static String wagonPoints() {
        return """
                select wagon, dt, x, y
                from wagon_geo_location
                where dt >= :from and dt < :to
                order by wagon, dt""";
    }

    /** Последнее положение каждого вагона до периода: wagon, dt, x, y. Параметр from - начало периода. */
    static String lastWagonPoints() {
        return """
                select distinct on (wagon) wagon, dt, x, y
                from wagon_geo_location
                where dt < :from
                order by wagon, dt desc""";
    }

    /**
     * Когда вагоны были в рейсах: trip, wagon, dbeg, dend - от отправления или прицепки до прибытия или отцепки; dend
     * null - вагон в рейсе. Параметры: from, to - период, fact - слой «факт» участия.
     */
    static String wagonTrips() {
        return """
                select trip.id as trip, train_wagon.wagon, greatest(trip.departed_dt, train_wagon.dbeg) as dbeg,
                       least(trip.arrived_dt, train_wagon.dend) as dend
                from trip
                join train_wagon on train_wagon.train = trip.train and train_wagon.kind = :fact
                     and (train_wagon.dend is null or train_wagon.dend > trip.departed_dt)
                     and (trip.arrived_dt is null or train_wagon.dbeg < trip.arrived_dt)
                where trip.departed_dt < :to
                  and (trip.arrived_dt is null or trip.arrived_dt > :from)""";
    }

    /**
     * Дежурства проводников в рейсах: trip, usr, dbeg, dend; dend null - на дежурстве. Параметры: from, to - период,
     * conductor - обязанность CrewDuty.
     */
    static String usrTrips() {
        return """
                select trip, usr, dbeg, dend
                from trip_crew
                where crew_duty_type = :conductor
                  and dbeg < :to
                  and (dend is null or dend > :from)""";
    }

    /** Положения проводников за период: usr, dt, x, y - по сотруднику и времени. Параметры: from, to, conductor. */
    static String usrPoints() {
        return """
                select usr, dt, x, y
                from usr_geo_location
                where dt >= :from and dt < :to
                  and usr in (select trip_crew.usr from trip_crew where trip_crew.crew_duty_type = :conductor)
                order by usr, dt""";
    }

    /** Последнее положение каждого проводника до периода: usr, dt, x, y. Параметры: from, conductor. */
    static String lastUsrPoints() {
        return """
                select distinct on (usr) usr, dt, x, y
                from usr_geo_location
                where dt < :from
                  and usr in (select trip_crew.usr from trip_crew where trip_crew.crew_duty_type = :conductor)
                order by usr, dt desc""";
    }

    /**
     * Положения проводников рейсов от отправления до прибытия рейса: trip, usr, dt, x, y - по рейсу, сотруднику и
     * времени. Параметры: trips - рейсы, conductor - обязанность CrewDuty, from, to - период.
     */
    static String tripUsrPoints() {
        return """
                select trip.id as trip, usr_geo_location.usr, usr_geo_location.dt, usr_geo_location.x,
                       usr_geo_location.y
                from trip
                join trip_crew on trip_crew.trip = trip.id and trip_crew.crew_duty_type = :conductor
                join usr_geo_location on usr_geo_location.usr = trip_crew.usr
                     and usr_geo_location.dt >= trip.departed_dt
                     and (trip.arrived_dt is null or usr_geo_location.dt <= trip.arrived_dt)
                where trip.id = any(:trips)
                  and usr_geo_location.dt >= :from and usr_geo_location.dt < :to
                order by trip.id, usr_geo_location.usr, usr_geo_location.dt""";
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
