package kz.zhsr.app.service;

import kz.zhsr.app.constants.TrainWagonChangeStatus;
import kz.zhsr.app.constants.TrainWagonChangeType;

/**
 * SQL поезда, участия вагонов в поезде и прицепок и отцепок ({@link TrainService}).
 * <p>
 * Участие вагона (train_wagon) по факту и по плану: начало - факт прицепки (dbeg), иначе плановое время прицепки;
 * конец - факт отцепки (dend), иначе плановое время отцепки, иначе без конца. Участие без начала (прицепка
 * отменена) не действует.
 */
final class TrainSql {

    /** Начало участия по факту или плану; null - участие не действует. */
    static final String BEG = """
            coalesce(train_wagon.dbeg,
                     (select train_wagon_change.planned_dt from train_wagon_change
                      where train_wagon_change.train_wagon = train_wagon.id
                        and train_wagon_change.change_type = %d and train_wagon_change.status = %d))"""
            .formatted(TrainWagonChangeType.ATTACH, TrainWagonChangeStatus.PLANNED);

    /** Конец участия по факту или плану; без конца - infinity. */
    static final String END = """
            coalesce(train_wagon.dend,
                     (select train_wagon_change.planned_dt from train_wagon_change
                      where train_wagon_change.train_wagon = train_wagon.id
                        and train_wagon_change.change_type = %d and train_wagon_change.status = %d),
                     'infinity'::timestamptz)"""
            .formatted(TrainWagonChangeType.DETACH, TrainWagonChangeStatus.PLANNED);

    /** Станция расположения вагона по wagon_info: сама станция, станция депо или парка отстоя; в рейсе - null. */
    static final String WAGON_STATION = """
            coalesce(wagon_info.station,
                     (select depot.station from depot where depot.id = wagon_info.depot),
                     (select yard.station from yard where yard.id = wagon_info.yard))""";

    private TrainSql() {
    }

    /**
     * Поезда: select без where и order by.
     */
    static String trains() {
        return """
                select train.id, train.name, train.dbeg, train.dend
                from train
                """;
    }

    /**
     * Вагоны поездов, которые действуют в промежутке [from, to): по факту и по плану, с расположением вагона,
     * началом и концом участия. Параметры: trains, from, to.
     */
    static String trainWagons() {
        return """
                select train_wagon.id, train_wagon.train, train_wagon.wagon, train_wagon.seq, train_wagon.dbeg,
                       train_wagon.dend, wagon.number, wagon.wagon_type, wagon_type.code,
                       wagon_info.location_type, wagon_info.trip, wagon_info.wagon_status_type,
                       coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard, wagon_info.trip) as place,
                       %1$s as station,
                       %2$s as beg,
                       nullif(%3$s, 'infinity'::timestamptz) as fin
                from train_wagon
                join wagon on wagon.id = train_wagon.wagon
                join wagon_type on wagon_type.id = wagon.wagon_type
                join wagon_info on wagon_info.wagon = wagon.id
                where train_wagon.train = any(:trains)
                  and %2$s is not null and %2$s < :to and %3$s > :from
                order by train_wagon.train, train_wagon.seq, train_wagon.id
                """.formatted(WAGON_STATION, BEG, END);
    }

    /**
     * Действующие участия вагонов, которые не закончились к from, - занятость вагонов. Параметры: wagons, from.
     */
    static String busy() {
        return """
                select train_wagon.id, train_wagon.train, train_wagon.wagon, wagon.number,
                       %1$s as beg,
                       nullif(%2$s, 'infinity'::timestamptz) as fin
                from train_wagon
                join wagon on wagon.id = train_wagon.wagon
                where train_wagon.wagon = any(:wagons)
                  and %1$s is not null and %2$s > :from
                order by %1$s
                """.formatted(BEG, END);
    }

    /**
     * Рейсы поездов в статусе «план», «выполняется» и «выполнен», с началом и концом: фактическим или плановым.
     * Параметры: trains, states.
     */
    static String trainTrips() {
        return """
                select trip.id, trip.train, trip.trip_status_type, trip.planned_dt,
                       coalesce(trip.departed_dt, trip.planned_dt) as beg,
                       coalesce(trip.arrived_dt, trip.planned_dt + make_interval(mins => %s)) as fin
                from trip
                join route on route.id = trip.route
                where trip.train = any(:trains) and trip.trip_status_type = any(:states)
                order by trip.planned_dt, trip.id
                """.formatted(RouteSql.DURATION);
    }

    /**
     * Поезда, у которых есть вагон с номером, содержащим search. Параметр search.
     */
    static String byWagonNumber() {
        return """
                select distinct train_wagon.train
                from train_wagon
                join wagon on wagon.id = train_wagon.wagon
                where strpos(wagon.number, :search) > 0 and train_wagon.dend is null
                """;
    }

    /**
     * Последняя позиция в поезде. Параметр train.
     */
    static String lastSeq() {
        return "select coalesce(max(train_wagon.seq), 0) as last_seq from train_wagon where train_wagon.train = :train";
    }

    /**
     * Рейс с маршрутом: поезд, статус, плановое и фактическое время. Параметр id.
     */
    static String trip() {
        return """
                select trip.id, trip.route, trip.train, trip.trip_status_type, trip.planned_dt, trip.departed_dt,
                       trip.arrived_dt
                from trip
                where trip.id = :id
                """;
    }

    /**
     * Крайние км маршрута. Параметр route.
     */
    static String routeEnds() {
        return """
                select min(route_station.km) as first_km, max(route_station.km) as last_km
                from route_station
                where route_station.route = :route
                """;
    }

    /**
     * Есть ли у поезда вагоны в поезде по факту или плановые прицепки. Параметр train.
     */
    static String alive() {
        return """
                select count(*) as cnt
                from train_wagon
                where train_wagon.train = :train
                  and ((train_wagon.dbeg is not null and train_wagon.dend is null)
                       or exists (select 1 from train_wagon_change
                                  where train_wagon_change.train_wagon = train_wagon.id
                                    and train_wagon_change.change_type = %d
                                    and train_wagon_change.status = %d))
                """.formatted(TrainWagonChangeType.ATTACH, TrainWagonChangeStatus.PLANNED);
    }

    /**
     * Строки бригады рейсов поезда в статусе «план», закреплённые за вагоном. Параметры train, wagon, planned.
     */
    static String plannedCrewOfWagon() {
        return """
                select trip_crew.id, trip_crew.trip, trip_crew.usr
                from trip_crew
                join trip on trip.id = trip_crew.trip
                where trip.train = :train and trip.trip_status_type = :planned and trip_crew.wagon = :wagon
                """;
    }

}
