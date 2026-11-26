package kz.zhsr.app.service;

/**
 * SQL поезда, журнала поезда (train_wagon_change, train_wagon_seq) и участия вагонов в поезде (train_wagon):
 * {@link TrainService}, {@link TrainJournal}.
 */
final class TrainSql {

    /** Станция расположения вагона по wagon_info: сама станция, станция депо или парка отстоя; в рейсе - null. */
    static final String WAGON_STATION = """
            coalesce(wagon_info.station,
                     (select depot.station from depot where depot.id = wagon_info.depot),
                     (select yard.station from yard where yard.id = wagon_info.yard))""";

    private TrainSql() {
    }

    /**
     * Станция расположения вагона на время по журналу расположения: station - сама станция, станция депо или парка отстоя;
     * в рейсе - null. Записи на это время нет - строки нет. Параметры wagon, time.
     */
    static String wagonStationAt() {
        return """
                select coalesce(wagon_location.station,
                                (select depot.station from depot where depot.id = wagon_location.depot),
                                (select yard.station from yard where yard.id = wagon_location.yard)) as station
                from wagon_location
                where wagon_location.wagon = :wagon and wagon_location.dbeg <= :time
                  and (wagon_location.dend is null or wagon_location.dend > :time)
                """;
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
     * Вагоны с текущими расположением и состоянием: поля TrainWagon. Параметр wagons.
     */
    static String wagons() {
        return """
                select wagon_info.wagon, wagon.number, wagon.wagon_type, wagon.model_type, wagon_type.code,
                       wagon_info.location_type, wagon_info.trip, wagon_info.wagon_status_type,
                       wagon_info.dbeg_status, wagon_info.wagon_responsible,
                       coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard, wagon_info.trip) as place,
                       %s as station
                from wagon_info
                join wagon on wagon.id = wagon_info.wagon
                join wagon_type on wagon_type.id = wagon.wagon_type
                where wagon_info.wagon = any(:wagons)
                """.formatted(WAGON_STATION);
    }

    /**
     * Открытые записи участия поездов слоя kind - текущий состав (слой «факт») с вагоном, его расположением и
     * состоянием, по позиции. Параметры trains, kind.
     */
    static String openWagons() {
        return """
                select train_wagon.train, train_wagon.wagon, train_wagon.seq, wagon.number, wagon.wagon_type,
                       wagon.model_type, wagon_info.location_type,
                       coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard, wagon_info.trip) as place,
                       %s as station
                from train_wagon
                join wagon on wagon.id = train_wagon.wagon
                join wagon_info on wagon_info.wagon = wagon.id
                where train_wagon.train = any(:trains) and train_wagon.kind = :kind and train_wagon.dend is null
                order by train_wagon.train, train_wagon.seq
                """.formatted(WAGON_STATION);
    }

    /** Открытые записи участия поезда слоя kind, по позиции. Параметры train, kind. */
    static String openStates() {
        return """
                select train_wagon.id, train_wagon.wagon, train_wagon.seq, train_wagon.dbeg
                from train_wagon
                where train_wagon.train = :train and train_wagon.kind = :kind and train_wagon.dend is null
                order by train_wagon.seq
                """;
    }

    /** Записи участия поезда слоя kind на момент time, по позиции. Параметры train, kind, time. */
    static String statesAt() {
        return """
                select train_wagon.id, train_wagon.wagon, train_wagon.seq, train_wagon.dbeg, train_wagon.dend
                from train_wagon
                where train_wagon.train = :train and train_wagon.kind = :kind and train_wagon.dbeg <= :time
                  and (train_wagon.dend is null or train_wagon.dend > :time)
                order by train_wagon.seq
                """;
    }

    /**
     * Записи участия поезда слоя kind, которые действуют в промежутке [from, to), по началу и позиции. Параметры
     * train, kind, from, to.
     */
    static String statesBetween() {
        return """
                select train_wagon.id, train_wagon.wagon, train_wagon.seq, train_wagon.dbeg, train_wagon.dend
                from train_wagon
                where train_wagon.train = :train and train_wagon.kind = :kind and train_wagon.dbeg < :to
                  and (train_wagon.dend is null or train_wagon.dend > :from)
                order by train_wagon.dbeg, train_wagon.seq
                """;
    }

    /** Удалить записи участия поезда слоя kind с началом не раньше time. Параметры train, kind, time. */
    static String deleteStatesFrom() {
        return """
                delete from train_wagon
                where train_wagon.train = :train and train_wagon.kind = :kind and train_wagon.dbeg >= :time
                """;
    }

    /** Снова открыть записи участия поезда слоя kind с концом не раньше time. Параметры train, kind, time. */
    static String reopenStatesFrom() {
        return """
                update train_wagon set dend = null
                where train_wagon.train = :train and train_wagon.kind = :kind and train_wagon.dend >= :time
                """;
    }

    /** Время последней записи Факта журнала поезда. Параметры train, fact. */
    static String lastTrainFact() {
        return """
                select greatest(
                           (select max(train_wagon_change.dt) from train_wagon_change
                            where train_wagon_change.train = :train and train_wagon_change.kind = :fact),
                           (select max(train_wagon_seq.dt) from train_wagon_seq
                            where train_wagon_seq.train = :train and train_wagon_seq.kind = :fact)) as dt
                """;
    }

    /** Время последней записи Факта журнала вагона во всех поездах. Параметры wagon, fact. */
    static String lastWagonFact() {
        return """
                select max(train_wagon_change.dt) as dt
                from train_wagon_change
                where train_wagon_change.wagon = :wagon and train_wagon_change.kind = :fact
                """;
    }

    /**
     * Последняя запись Факта прицепки вагона к поезду не позже time. Параметры train, wagon, time, fact, attach.
     */
    static String attachOf() {
        return """
                select train_wagon_change.id
                from train_wagon_change
                where train_wagon_change.train = :train and train_wagon_change.wagon = :wagon
                  and train_wagon_change.kind = :fact and train_wagon_change.change_type = :attach
                  and train_wagon_change.dt <= :time
                order by train_wagon_change.dt desc, train_wagon_change.id desc
                limit 1
                """;
    }

    /** Число ожидающих записей Плана по поездам: до двух строк на поезд. Параметры trains, plan, waiting. */
    static String waitingCounts() {
        return """
                select train_wagon_change.train, count(*) as cnt
                from train_wagon_change
                where train_wagon_change.train = any(:trains) and train_wagon_change.kind = :plan
                  and train_wagon_change.plan_status = :waiting
                group by train_wagon_change.train
                union all
                select train_wagon_seq.train, count(*) as cnt
                from train_wagon_seq
                where train_wagon_seq.train = any(:trains) and train_wagon_seq.kind = :plan
                  and train_wagon_seq.plan_status = :waiting
                group by train_wagon_seq.train
                """;
    }

    /**
     * Занятость вагонов с времени from по ожидаемому составу поездов (слой «план»: в нём и вагоны, которые в поезде
     * по факту), которая не закончилась к from, одной строкой на вагон и поезд - с какого и до какого времени.
     * Параметры wagons, from, plan.
     */
    static String busy() {
        return """
                select train_wagon.wagon, wagon.number, train_wagon.train, min(train_wagon.dbeg) as beg,
                       case when bool_or(train_wagon.dend is null) then null else max(train_wagon.dend) end as fin
                from train_wagon
                join wagon on wagon.id = train_wagon.wagon
                where train_wagon.wagon = any(:wagons) and train_wagon.kind = :plan
                  and (train_wagon.dend is null or train_wagon.dend > :from)
                group by train_wagon.wagon, wagon.number, train_wagon.train
                order by beg
                """;
    }

    /**
     * Рейсы поездов в статусе «план», «выполняется» и «выполнен», с началом и концом: фактическим или плановым.
     * Параметры trains, states.
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
     * Рейсы поезда для отметок журнала, по плановому отправлению. Поля: id, trip_status_type, planned_dt, departed_dt,
     * arrived_dt, arrival (плановое прибытие). Параметры: train, states.
     */
    static String tripMarks() {
        return """
                select trip.id, trip.trip_status_type, trip.planned_dt, trip.departed_dt, trip.arrived_dt,
                       trip.planned_dt + make_interval(mins => %s) as arrival
                from trip
                join route on route.id = trip.route
                where trip.train = :train and trip.trip_status_type = any(:states)
                order by trip.planned_dt, trip.id
                """.formatted(RouteSql.DURATION);
    }

    /**
     * Поезда, у которых сейчас по факту есть вагон с номером, содержащим search. Параметры search, fact.
     */
    static String byWagonNumber() {
        return """
                select distinct train_wagon.train
                from train_wagon
                join wagon on wagon.id = train_wagon.wagon
                where strpos(wagon.number, :search) > 0 and train_wagon.kind = :fact and train_wagon.dend is null
                """;
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
     * Есть ли у поезда вагоны по факту или ожидающие плановые прицепки. Параметры train, fact, plan, waiting, attach.
     */
    static String alive() {
        return """
                select (select count(*) from train_wagon
                        where train_wagon.train = :train and train_wagon.kind = :fact and train_wagon.dend is null)
                     + (select count(*) from train_wagon_change
                        where train_wagon_change.train = :train and train_wagon_change.kind = :plan
                          and train_wagon_change.plan_status = :waiting
                          and train_wagon_change.change_type = :attach) as cnt
                """;
    }

}
