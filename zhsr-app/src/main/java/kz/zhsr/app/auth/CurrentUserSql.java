package kz.zhsr.app.auth;

/**
 * SQL {@link CurrentUserService}: запросы с соединениями и условиями, которые не покрываются методами Db.
 * Параметры запросов - в описании каждого метода.
 */
final class CurrentUserSql {

    private CurrentUserSql() {
    }

    /**
     * Роли сотрудника (id, service) по id роли. Параметр: usr.
     */
    static String roles() {
        return """
                select role_type.id, role_type.service
                from usr_role
                join role_type on role_type.id = usr_role.role_type
                where usr_role.usr = :usr
                order by role_type.id
                """;
    }

    /**
     * Текущее депо сотрудника (id, station) - незакрытая запись usr_depot: она одна или её нет. Параметр: usr.
     */
    static String currentDepot() {
        return """
                select depot.id, depot.station
                from usr_depot
                join depot on depot.id = usr_depot.depot
                where usr_depot.usr = :usr and usr_depot.dend is null
                """;
    }

    /**
     * Текущий рейс сотрудника: из рейсов его бригады - выполняется, иначе ближайший по плановому отправлению из
     * запланированных. Поля: id, train_number, name (маршрута), trip_status_type, crew_duty_type, wagon и wagon2 (вагоны
     * проводника), number и number2 (номера этих вагонов), departure (фактическое или плановое отправление), arrival (плановое
     * прибытие: плановое отправление плюс время в пути маршрута, route.duration). Параметры: usr, inProgress, planned -
     * статусы рейса.
     */
    static String currentTrip() {
        return """
                select trip.id, route.train_number, route.name, trip.trip_status_type, trip_crew.crew_duty_type,
                       trip_crew.wagon, wagon.number, trip_crew.wagon2,
                       (select second_wagon.number from wagon second_wagon where second_wagon.id = trip_crew.wagon2) as number2,
                       coalesce(trip.departed_dt, trip.planned_dt) as departure,
                       trip.planned_dt + make_interval(mins => route.duration) as arrival
                from trip_crew
                join trip on trip.id = trip_crew.trip
                join route on route.id = trip.route
                left join wagon on wagon.id = trip_crew.wagon
                where trip_crew.usr = :usr and trip.trip_status_type in (:inProgress, :planned)
                order by trip.trip_status_type = :inProgress desc, trip.planned_dt
                limit 1
                """;
    }

}
