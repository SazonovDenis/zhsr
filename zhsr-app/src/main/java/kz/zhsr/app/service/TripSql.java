package kz.zhsr.app.service;

/**
 * SQL формирования рейса, бригады, отправления и прибытия ({@link TripService}) и прицепки в рейсе
 * ({@link CouplingService}).
 */
final class TripSql {

    // плановое прибытие: плановое отправление плюс время в пути маршрута (по станциям маршрута, мин)
    private static final String ARRIVAL = "trip.planned_dt + make_interval(mins => " + RouteSql.DURATION + ") as arrival";

    // старший проводник: текущая категория сотрудника usr
    private static final String SENIOR = """
            exists (select 1 from usr_category
                    where usr_category.usr = usr.id and usr_category.dend is null
                      and usr_category.category_type = :senior) as senior""";

    private TripSql() {
    }

    /**
     * Первое изменение после отметки отправления или прибытия: записи журналов вагонов, которые были в поезде в момент
     * отметки, прицепки и отцепки поезда и строки бригады рейса - позже отметки; номер вагона (ФИО сотрудника
     * бригады) и время. Записи, которые открыла сама отметка, начинаются временем отметки и не попадают. Не попадают и
     * назначения ответственного за вагоны рейса после прибытия - записи ответственного без документа (прицепки и
     * отцепки, акта, ручной правки): отмена прибытия снимает их сама. Параметры: train, trip, since - время отметки.
     */
    static String changesAfter() {
        String inTrain = """
                train_wagon.train = :train and train_wagon.dbeg <= :since
                  and (train_wagon.dend is null or train_wagon.dend > :since)""";
        return """
                select wagon.number, wagon_location.dbeg
                from train_wagon
                join wagon_location on wagon_location.wagon = train_wagon.wagon
                join wagon on wagon.id = train_wagon.wagon
                where %1$s and wagon_location.dbeg > :since
                union all
                select wagon.number, wagon_status.dbeg
                from train_wagon
                join wagon_status on wagon_status.wagon = train_wagon.wagon
                join wagon on wagon.id = train_wagon.wagon
                where %1$s and wagon_status.dbeg > :since
                union all
                select wagon.number, wagon_responsible.dbeg
                from train_wagon
                join wagon_responsible on wagon_responsible.wagon = train_wagon.wagon
                join wagon on wagon.id = train_wagon.wagon
                where %1$s and wagon_responsible.dbeg > :since
                  and (exists (select 1 from train_wagon_change
                               where train_wagon_change.wagon_responsible = wagon_responsible.id)
                    or exists (select 1 from transfer_act_wagon
                               where transfer_act_wagon.wagon_responsible = wagon_responsible.id)
                    or exists (select 1 from wagon_edit where wagon_edit.wagon_responsible = wagon_responsible.id))
                union all
                select wagon.number, train_wagon_change.fact_dt
                from train_wagon_change
                join train_wagon on train_wagon.id = train_wagon_change.train_wagon
                join wagon on wagon.id = train_wagon.wagon
                where train_wagon.train = :train and train_wagon_change.fact_dt > :since
                union all
                select usr.full_name, trip_crew.dbeg
                from trip_crew
                join usr on usr.id = trip_crew.usr
                where trip_crew.trip = :trip and trip_crew.dbeg > :since
                order by dbeg
                limit 1
                """.formatted(inTrain);
    }

    /**
     * Рейс с маршрутом и поездом. Параметр: id.
     */
    static String trip() {
        return """
                select trip.id, trip.route, trip.train, route.train_number, route.name, trip.trip_status_type,
                       route.transport_type, %1$s as duration, trip.planned_dt, trip.departed_dt,
                       trip.arrived_dt, trip.departure_marked_dt, trip.arrival_marked_dt,
                       coalesce(trip.departed_dt, trip.planned_dt) as departure,
                       %2$s
                from trip
                join route on route.id = trip.route
                where trip.id = :id
                """.formatted(RouteSql.DURATION, ARRIVAL);
    }

    /**
     * Действующие строки бригады рейса: в рейсе в статусе «план» и «выполняется» - незакрытые, в закончившемся - и
     * закрытые прибытием. Параметры: trip, senior - категория «старший проводник», arrived - время прибытия рейса
     * (null - рейс не прибыл).
     */
    static String crew() {
        return """
                select trip_crew.id, trip_crew.usr, trip_crew.crew_duty_type, trip_crew.wagon,
                       %s
                from trip_crew
                join usr on usr.id = trip_crew.usr
                where trip_crew.trip = :trip
                  and (trip_crew.dend is null or trip_crew.dend = cast(:arrived as timestamptz))
                order by usr.full_name, trip_crew.usr, trip_crew.id
                """.formatted(SENIOR);
    }

    /**
     * Проводники станции отправления (текущее депо сотрудника - при этой станции) и уже назначенные в рейс.
     * Параметры: trip, station, duty - должность «проводник», role - роль «проводник», senior.
     */
    static String conductorCandidates() {
        return """
                select usr.id,
                       %s
                from usr
                where usr.id in (select trip_crew.usr from trip_crew
                                 where trip_crew.trip = :trip and trip_crew.crew_duty_type = :duty)
                   or (not usr.disabled
                       and exists (select 1 from usr_role
                                   where usr_role.usr = usr.id and usr_role.role_type = :role)
                       and exists (select 1 from usr_depot
                                   join depot on depot.id = usr_depot.depot
                                   where usr_depot.usr = usr.id and usr_depot.dend is null
                                     and depot.station = :station))
                order by usr.full_name, usr.id
                """.formatted(SENIOR);
    }

    /**
     * Включённые сотрудники с ролью. Параметр: role.
     */
    static String staffCandidates() {
        return """
                select usr.id
                from usr
                join usr_role on usr_role.usr = usr.id
                where usr_role.role_type = :role and not usr.disabled
                order by usr.full_name, usr.id
                """;
    }

    /**
     * Другие рейсы, в бригадах которых состоят сотрудники. Параметры: usrs, trip - кроме этого рейса, states. arrival -
     * до какого времени рейс занимает сотрудника: фактическое прибытие, а если его нет - плановое.
     */
    static String otherTrips() {
        return """
                select distinct trip_crew.usr, trip_crew.trip, route.train_number, route.name, trip.planned_dt,
                       coalesce(trip.arrived_dt, trip.planned_dt + make_interval(mins => %s)) as arrival
                from trip_crew
                join trip on trip.id = trip_crew.trip
                join route on route.id = trip.route
                where trip_crew.usr = any(:usrs) and trip_crew.trip <> :trip
                  and trip.trip_status_type = any(:states)
                """.formatted(RouteSql.DURATION);
    }

    /**
     * Вагоны станции, её депо и парка отстоя для прицепки. Параметр: station.
     */
    static String attachCandidates() {
        return """
                select wagon.id, wagon.number, wagon.wagon_type, wagon_info.location_type,
                       coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard) as place,
                       wagon_info.wagon_status_type
                from wagon
                join wagon_info on wagon_info.wagon = wagon.id
                left join depot on depot.id = wagon_info.depot
                left join yard on yard.id = wagon_info.yard
                where (wagon_info.station = :station or depot.station = :station or yard.station = :station)
                  and (wagon.dend is null or wagon.dend >= current_date)
                order by wagon.number
                """;
    }

    /**
     * Время в пути маршрута, мин. Параметр: route.
     */
    static String routeDuration() {
        return "select %s as duration from route where route.id = :route".formatted(RouteSql.DURATION);
    }

    /**
     * Рейсы поезда с началом и концом: фактическим или плановым. Параметры: train, states.
     */
    static String trainTrips() {
        return """
                select trip.id, route.train_number, route.name,
                       coalesce(trip.departed_dt, trip.planned_dt) as beg,
                       coalesce(trip.arrived_dt, trip.planned_dt + make_interval(mins => %s)) as fin
                from trip
                join route on route.id = trip.route
                where trip.train = :train and trip.trip_status_type = any(:states)
                """.formatted(RouteSql.DURATION);
    }

}
