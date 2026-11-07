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
     * Рейс сотрудника из рейсов его бригады в статусе status, первый по order (выражение сортировки из кода, не от
     * пользователя). Поля: id, train_number, name (маршрута), trip_status_type, crew_duty_type, wagon и wagon2 (вагоны
     * проводника), number и number2 (номера этих вагонов), departure (фактическое или плановое отправление), arrival
     * (плановое прибытие: плановое отправление плюс время в пути маршрута, route.duration), planned_dt, departed_dt,
     * arrived_dt, departure_marked_dt, arrival_marked_dt. Параметры: usr, status.
     */
    static String trip(String order) {
        return """
                select trip.id, route.train_number, route.name, trip.trip_status_type, trip_crew.crew_duty_type,
                       trip_crew.wagon, wagon.number, trip_crew.wagon2,
                       (select second_wagon.number from wagon second_wagon where second_wagon.id = trip_crew.wagon2) as number2,
                       coalesce(trip.departed_dt, trip.planned_dt) as departure,
                       trip.planned_dt + make_interval(mins => route.duration) as arrival,
                       trip.planned_dt, trip.departed_dt, trip.arrived_dt, trip.departure_marked_dt,
                       trip.arrival_marked_dt
                from trip_crew
                join trip on trip.id = trip_crew.trip
                join route on route.id = trip.route
                left join wagon on wagon.id = trip_crew.wagon
                where trip_crew.usr = :usr and trip.trip_status_type = :status
                order by %s
                limit 1
                """.formatted(order);
    }

}
