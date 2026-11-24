package kz.zhsr.app.auth;

import kz.zhsr.app.service.RouteSql;
import kz.zhsr.app.service.TripLayerSql;

/**
 * SQL {@link CurrentUserService}: запросы с соединениями и условиями, которые не покрываются методами Db.
 * Параметры запросов - в описании каждого метода.
 */
final class CurrentUserSql {

    private CurrentUserSql() {
    }

    /**
     * Роли сотрудника (id, service) по названию роли. Параметр: usr.
     */
    static String roles() {
        return """
                select role_type.id, role_type.service
                from usr_role
                join role_type on role_type.id = usr_role.role_type
                where usr_role.usr = :usr
                order by role_type.text
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
     * пользователя). Поля: id, train_number, name (маршрута), trip_status_type, crew_duty_type, departure
     * (фактическое или плановое отправление), arrival (плановое прибытие: плановое отправление плюс время в пути
     * маршрута по его станциям), planned_dt, departed_dt, arrived_dt, departure_marked_dt, arrival_marked_dt.
     * Параметры: usr, status.
     */
    static String trip(String order) {
        return """
                select trip.id, route.train_number, route.name, trip.trip_status_type, trip_crew.crew_duty_type,
                       coalesce(trip.departed_dt, trip.planned_dt) as departure,
                       trip.planned_dt + make_interval(mins => %2$s) as arrival,
                       trip.planned_dt, trip.departed_dt, trip.arrived_dt, trip.departure_marked_dt,
                       trip.arrival_marked_dt
                from trip_crew
                join trip on trip.id = trip_crew.trip
                join route on route.id = trip.route
                where trip_crew.usr = :usr and trip.trip_status_type = :status
                order by %1$s, route.train_number
                limit 1
                """.formatted(order, RouteSql.DURATION);
    }

    /**
     * Вагоны, за которые сотрудник отвечает на рейс trip: журнал ответственных (условие {@link TripLayerSql#WHERE}),
     * по номеру. Параметры: usr, trip и {@link TripLayerSql#params}.
     */
    static String tripWagons() {
        return """
                select wagon.id as wagon, wagon.number
                from wagon_responsible
                join wagon_responsible_usr on wagon_responsible_usr.wagon_responsible = wagon_responsible.id
                join wagon on wagon.id = wagon_responsible.wagon
                join trip on trip.id = :trip
                where wagon_responsible_usr.usr = :usr and %s
                order by wagon.number
                """.formatted(TripLayerSql.WHERE);
    }

}
