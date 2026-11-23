package kz.zhsr.app.service.analytics;

/**
 * SQL {@link AnalyticsService}. Параметры запросов - в описании каждого метода.
 */
final class AnalyticsSql {

    private AnalyticsSql() {
    }

    /**
     * Участие в отправленных рейсах: usr, crew_duty_type, departed_dt и часы рейса по обязанности (route.lnp_hours
     * для ЛНП, route.conductor_hours для проводника). Параметры: from - начало периода по departed_dt, lnp,
     * conductor - обязанности CrewDuty.
     */
    static String crewHours() {
        return """
                select trip_crew.usr, trip_crew.crew_duty_type, trip.departed_dt,
                       case when trip_crew.crew_duty_type = :lnp then route.lnp_hours
                            else route.conductor_hours end as hours
                from trip_crew
                join trip on trip.id = trip_crew.trip
                join route on route.id = trip.route
                where trip.departed_dt >= :from
                  and trip_crew.crew_duty_type in (:lnp, :conductor)""";
    }

    /** Текущие депо сотрудников: usr, depot (usr_depot с dend is null). Без параметров. */
    static String currentDepots() {
        return "select usr, depot from usr_depot where dend is null";
    }

    /**
     * Вагоны в эксплуатации на момент at: wagon, depot, last_check_dt, last_equip_dt, last_trip_dt. Рейс вагона - отправленный рейс
     * поезда, в котором вагон числился по факту между отправлением и прибытием (прибытия нет - по момент at).
     * Параметры: fact - слой «факт» участия, at - момент; позднее него ничего не учитывается.
     */
    static String lastChecks() {
        return """
                select wagon_info.wagon, wagon.depot,
                       (select max(check_list.completed_dt) from check_list
                        where check_list.wagon = wagon.id and check_list.completed_dt <= :at) as last_check_dt,
                       (select max(equip_list.completed_dt) from equip_list
                        where equip_list.wagon = wagon.id and equip_list.completed_dt <= :at) as last_equip_dt,
                       (select max(trip.departed_dt) from train_wagon
                        join trip on trip.train = train_wagon.train
                        where train_wagon.wagon = wagon.id and train_wagon.kind = :fact
                          and trip.departed_dt <= :at
                          and train_wagon.dbeg <= :at
                          and (trip.arrived_dt is null or train_wagon.dbeg <= trip.arrived_dt)
                          and (train_wagon.dend is null or train_wagon.dend > trip.departed_dt)) as last_trip_dt
                from wagon_info
                join wagon on wagon.id = wagon_info.wagon
                where (wagon.dbeg is null or wagon.dbeg <= cast(:at as date))
                  and (wagon.dend is null or wagon.dend >= cast(:at as date))
                order by wagon.number""";
    }

    /**
     * Заявки: created_dt, done_dt - начало последнего выполнения; null - заявка сейчас не выполнена и не закрыта.
     * Параметры: from - начало периода по created_dt, done и closed - статусы RequestStatus, service - служба (только
     * при byService).
     */
    static String requests(boolean byService) {
        return """
                select repair_request.created_dt,
                       case when repair_request_info.request_status_type in (:done, :closed)
                            then (select max(repair_request_status.dbeg) from repair_request_status
                                  where repair_request_status.repair_request = repair_request.id
                                    and repair_request_status.request_status_type = :done)
                       end as done_dt
                from repair_request
                join repair_request_info on repair_request_info.repair_request = repair_request.id
                where repair_request.created_dt >= :from"""
                + (byService ? " and repair_request_info.service = :service" : "");
    }

    /**
     * Взятия в работу и выполнения заявок по порядку: repair_request, dbeg, request_status_type, executor_usr -
     * исполнитель заявки в момент dbeg; null - исполнителя не было. Параметры: from - начало периода по
     * repair_request.created_dt, service - служба, inProgress и done - статусы RequestStatus.
     */
    static String execution() {
        return """
                select repair_request_status.repair_request, repair_request_status.dbeg,
                       repair_request_status.request_status_type, repair_request_executor.executor_usr
                from repair_request_status
                join repair_request on repair_request.id = repair_request_status.repair_request
                join repair_request_info on repair_request_info.repair_request = repair_request.id
                left join repair_request_executor on repair_request_executor.repair_request = repair_request.id
                     and repair_request_executor.dbeg <= repair_request_status.dbeg
                     and (repair_request_executor.dend is null
                          or repair_request_executor.dend > repair_request_status.dbeg)
                where repair_request.created_dt >= :from
                  and repair_request_info.service = :service
                  and repair_request_status.request_status_type in (:inProgress, :done)
                order by repair_request_status.repair_request, repair_request_status.dbeg, repair_request_status.id""";
    }

    /**
     * Взятия в работу и выполнения заявок всех служб по порядку: repair_request, service, fault_type, dbeg,
     * request_status_type, executor_usr - исполнитель заявки в момент dbeg; null - исполнителя не было. Параметры: from
     * и to - период [from, to) по repair_request.created_dt, inProgress и done - статусы RequestStatus.
     */
    static String workTime() {
        return """
                select repair_request_status.repair_request, repair_request_info.service, repair_request.fault_type,
                       repair_request_status.dbeg, repair_request_status.request_status_type,
                       repair_request_executor.executor_usr
                from repair_request_status
                join repair_request on repair_request.id = repair_request_status.repair_request
                join repair_request_info on repair_request_info.repair_request = repair_request.id
                left join repair_request_executor on repair_request_executor.repair_request = repair_request.id
                     and repair_request_executor.dbeg <= repair_request_status.dbeg
                     and (repair_request_executor.dend is null
                          or repair_request_executor.dend > repair_request_status.dbeg)
                where repair_request.created_dt >= :from
                  and repair_request.created_dt < :to
                  and repair_request_status.request_status_type in (:inProgress, :done)
                order by repair_request_status.repair_request, repair_request_status.dbeg, repair_request_status.id""";
    }

    /** Отправленные рейсы: departed_dt. Параметр from - начало периода. */
    static String departures() {
        return "select departed_dt from trip where departed_dt >= :from";
    }
}
