package kz.zhsr.app.service;

/**
 * SQL формирования рейса, отправления и прибытия ({@link TripService}).
 */
final class TripSql {

    // плановое прибытие: плановое отправление плюс время в пути маршрута (route.duration, мин; вводит пользователь)
    private static final String ARRIVAL = """
            trip.planned_dt + make_interval(mins => route.duration) as arrival""";

    // старший проводник: текущая категория сотрудника usr
    private static final String SENIOR = """
            exists (select 1 from usr_category
                    where usr_category.usr = usr.id and usr_category.dend is null
                      and usr_category.category_type = :senior) as senior""";

    private TripSql() {
    }

    /**
     * Рейс с маршрутом. Параметр: id.
     */
    static String trip() {
        return """
                select trip.id, trip.route, route.train_number, route.name, trip.trip_status_type,
                       route.transport_type, route.duration, trip.planned_dt, trip.departed_dt,
                       coalesce(trip.departed_dt, trip.planned_dt) as departure,
                       %s
                from trip
                join route on route.id = trip.route
                where trip.id = :id
                """.formatted(ARRIVAL);
    }

    /**
     * Состав рейса по порядку, с типом вагона и текущим расположением; last_coupling - вид последнего проведённого
     * акта по вагону в этом рейсе. Параметры: trip, done - состояние акта «проведён».
     */
    static String composition() {
        return """
                select trip_wagon.id, trip_wagon.wagon, trip_wagon.seq, wagon.number, wagon.wagon_type, wagon_type.code,
                       wagon_info.location_type, wagon_info.station, wagon_info.trip, wagon_info.wagon_status_type,
                       coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard, wagon_info.trip) as place,
                       route.train_number, route.name,
                       (select coupling_act.coupling_type
                        from coupling_act_wagon
                        join coupling_act on coupling_act.id = coupling_act_wagon.coupling_act
                        where coupling_act.trip = trip_wagon.trip and coupling_act_wagon.wagon = trip_wagon.wagon
                          and coupling_act.act_status_type = :done
                        order by coupling_act.id desc
                        limit 1) as last_coupling
                from trip_wagon
                join wagon on wagon.id = trip_wagon.wagon
                join wagon_type on wagon_type.id = wagon.wagon_type
                join wagon_info on wagon_info.wagon = wagon.id
                left join trip on trip.id = wagon_info.trip
                left join route on route.id = trip.route
                where trip_wagon.trip = :trip
                order by trip_wagon.seq, trip_wagon.id
                """;
    }

    /**
     * Бригада рейса. Параметры: trip, senior - категория «старший проводник».
     */
    static String crew() {
        return """
                select trip_crew.id, trip_crew.usr, trip_crew.crew_duty_type, trip_crew.wagon, trip_crew.wagon2,
                       usr.full_name,
                       %s
                from trip_crew
                join usr on usr.id = trip_crew.usr
                where trip_crew.trip = :trip
                order by usr.full_name, trip_crew.id
                """.formatted(SENIOR);
    }

    /**
     * Проводники станции отправления (текущее депо сотрудника - при этой станции) и уже назначенные в рейс.
     * Параметры: trip, station, duty - должность «проводник», role - роль «проводник», senior.
     */
    static String conductorCandidates() {
        return """
                select usr.id, usr.full_name,
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
                select usr.id, usr.full_name
                from usr
                join usr_role on usr_role.usr = usr.id
                where usr_role.role_type = :role and not usr.disabled
                order by usr.full_name, usr.id
                """;
    }

    /**
     * Другие рейсы, в бригадах которых состоят сотрудники. Параметры: usrs, trip - кроме этого рейса, states.
     */
    static String otherTrips() {
        return """
                select trip_crew.usr, route.train_number, route.name, trip.planned_dt,
                       %s
                from trip_crew
                join trip on trip.id = trip_crew.trip
                join route on route.id = trip.route
                where trip_crew.usr = any(:usrs) and trip_crew.trip <> :trip
                  and trip.trip_status_type = any(:states)
                """.formatted(ARRIVAL);
    }

    /**
     * Свободные вагоны на станции отправления, в депо и в парке отстоя при ней: не списаны и не входят в состав
     * другого активного рейса; вагоны этого рейса не показываются. Параметры: trip, station, states.
     */
    static String wagonCandidates() {
        return """
                select wagon.number, wagon.wagon_type, wagon_info.location_type,
                       coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard) as place,
                       wagon_info.wagon_status_type
                from wagon
                join wagon_info on wagon_info.wagon = wagon.id
                left join depot on depot.id = wagon_info.depot
                left join yard on yard.id = wagon_info.yard
                where (wagon_info.station = :station or depot.station = :station or yard.station = :station)
                  and (wagon.dend is null or wagon.dend >= current_date)
                  and not exists (select 1 from trip_wagon
                                  join trip on trip.id = trip_wagon.trip
                                  where trip_wagon.wagon = wagon.id
                                    and (trip.id = :trip or trip.trip_status_type = any(:states)))
                order by wagon.number
                """;
    }

    /**
     * Вагоны станции, её депо и парка отстоя для прицепки; other_train - другой активный рейс, в составе которого
     * вагон. Параметры: trip, station, states.
     */
    static String attachCandidates() {
        return """
                select wagon.number, wagon.wagon_type, wagon_info.location_type,
                       coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard) as place,
                       wagon_info.wagon_status_type,
                       (select route.train_number
                        from trip_wagon
                        join trip on trip.id = trip_wagon.trip
                        join route on route.id = trip.route
                        where trip_wagon.wagon = wagon.id and trip.id <> :trip
                          and trip.trip_status_type = any(:states)
                        limit 1) as other_train
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
     * Акты отцепки и прицепки рейса, новые первыми. Параметр: trip.
     */
    static String acts() {
        return """
                select coupling_act.id, coupling_act.number, coupling_act.coupling_type, route_station.station,
                       coupling_act.act_dt, coupling_act.act_status_type
                from coupling_act
                join route_station on route_station.id = coupling_act.route_station
                where coupling_act.trip = :trip
                order by coupling_act.id desc
                """;
    }

    /**
     * Номера вагонов актов рейса. Параметр: trip.
     */
    static String actWagons() {
        return """
                select coupling_act_wagon.coupling_act, wagon.number
                from coupling_act_wagon
                join coupling_act on coupling_act.id = coupling_act_wagon.coupling_act
                join wagon on wagon.id = coupling_act_wagon.wagon
                where coupling_act.trip = :trip
                order by coupling_act_wagon.coupling_act, coupling_act_wagon.seq
                """;
    }

    /**
     * Следующий номер для акта отцепки или прицепки.
     */
    static String nextActId() {
        return "select coalesce(max(coupling_act.id), 0) + 1 as next_id from coupling_act";
    }

    /**
     * Последняя позиция состава рейса. Параметр: trip.
     */
    static String lastSeq() {
        return "select coalesce(max(trip_wagon.seq), 0) as last_seq from trip_wagon where trip_wagon.trip = :trip";
    }

    /**
     * Вагоны из списка, которые входят в состав другого активного рейса. Параметры: wagons, trip, states.
     */
    static String busyWagons() {
        return """
                select wagon.number
                from trip_wagon
                join trip on trip.id = trip_wagon.trip
                join wagon on wagon.id = trip_wagon.wagon
                where trip_wagon.wagon = any(:wagons) and trip_wagon.trip <> :trip
                  and trip.trip_status_type = any(:states)
                order by wagon.number
                """;
    }

}
