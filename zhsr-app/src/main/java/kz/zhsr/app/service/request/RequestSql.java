package kz.zhsr.app.service.request;

import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RequestStatus;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.request.RequestFilter;
import kz.zhsr.app.service.SqlQuery;
import kz.zhsr.app.service.SqlWhere;

import java.util.List;
import java.util.Map;

/**
 * SQL заявок на ремонт ({@link RequestService}): запросы с соединениями и условиями, которые не покрываются методами
 * Db. Логики здесь нет - только сборка запроса.
 * <p>
 * Номер вагона, имена сотрудников и названия рейсов в строку заявки не выбираются: колонки называются так же, как
 * колонки заявки (number, trip, created_by_usr), - их догружает сервис методами Db.
 */
final class RequestSql {

    private RequestSql() {
    }

    /**
     * Поле строки (zhsr-web) -> выражение сортировки.
     */
    private static final Map<String, String> SORT = Map.of(
            "createdDt", "repair_request.created_dt",
            "wagonNumber", "wagon.number",
            "requestStatusType", "repair_request_info.request_status_type"
    );

    /**
     * Заявка, её текущее состояние, вагон и где он сейчас (станция депо и парка отстоя - через depot и yard).
     */
    private static final String FROM = """
            from repair_request
            join repair_request_info on repair_request_info.repair_request = repair_request.id
            join wagon on wagon.id = repair_request.wagon
            left join wagon_info on wagon_info.wagon = wagon.id
            left join depot on depot.id = wagon_info.depot
            left join yard on yard.id = wagon_info.yard
            """;

    /**
     * Заявка видна сотруднику для исполнения (Ф-ЗАЯ-06) - единственное место этого правила: им отбирается список
     * «на исполнение» и проверяются действия «взять в работу» и «отклонить» ({@link RequestRights}). Видна, если
     * сотрудник - администратор или у одной из его ролей служба заявки и: вагон в рейсе - это текущий рейс
     * сотрудника; вагон не в рейсе - вагон на станции депо сотрудника (станция, депо или парк отстоя этой станции).
     * <p>
     * Параметры: admin - сотрудник администратор; services - службы ролей сотрудника; myTrip - текущий рейс
     * сотрудника (null - нет); myDepotStation - станция депо сотрудника (null - нет); tripLocation -
     * LocationType.TRIP.
     */
    private static final String VISIBLE = """
            (:admin
             or (repair_request_info.service = any(:services)
                 and ((wagon_info.location_type = :tripLocation and wagon_info.trip = :myTrip)
                      or (wagon_info.location_type <> :tripLocation
                          and coalesce(wagon_info.station, depot.station, yard.station) = :myDepotStation))))""";

    /**
     * Сотрудник принимает работу по заявке (Ф-ЗАЯ-03) - то же правило, что {@link RequestRights} canAccept: вагон в
     * рейсе - текущий рейс сотрудника, а он ЛНП рейса или проводник этого вагона; вагон не в рейсе - сотрудник
     * инструктор поездных бригад или эксплуатационщик, депо которого на станции вагона. Администратор - всегда.
     * <p>
     * Параметры: admin, myTrip, myDepotStation, tripLocation - как у {@link #VISIBLE}; myLnp - сотрудник ЛНП своего
     * рейса; myWagons - вагоны, за которыми он закреплён проводником; acceptor - роль инструктора или
     * эксплуатационщика.
     */
    private static final String ACCEPTABLE = """
            (:admin
             or (wagon_info.location_type = :tripLocation and wagon_info.trip = :myTrip
                 and (:myLnp or wagon.id = any(:myWagons)))
             or (wagon_info.location_type <> :tripLocation and :acceptor
                 and coalesce(wagon_info.station, depot.station, yard.station) = :myDepotStation))""";

    /**
     * Заявка ждёт действия лично сотрудника - список «мне на исполнение»: назначена ему или в работе у него; выполнена
     * и он принимает работу ({@link #ACCEPTABLE}).
     * <p>
     * Параметры: me, assigned, inProgress, done - статусы (request_status_type); параметры {@link #ACCEPTABLE}.
     */
    private static final String TODO = """
            ((repair_request_info.request_status_type in (:assigned, :inProgress)
              and repair_request_info.executor_usr = :me)
             or (repair_request_info.request_status_type = :done and """ + ACCEPTABLE + """
            ))""";

    /**
     * Заявка видна бригаде текущего рейса сотрудника, независимо от службы и исполнителя: ЛНП - по всем вагонам, которые
     * сейчас в составе его рейса; проводник - по вагонам, за которыми он закреплён. Параметры: myTrip, myLnp,
     * myWagons, tripLocation - как у {@link #ACCEPTABLE}.
     */
    private static final String CREW = """
            (wagon_info.location_type = :tripLocation and wagon_info.trip = :myTrip
             and (:myLnp or wagon.id = any(:myWagons)))""";

    /**
     * Заявка видна сотруднику - список «все»: видна ему для исполнения по службе ({@link #VISIBLE}), видна бригаде
     * его рейса ({@link #CREW}), ждёт его действия ({@link #TODO}) или подана им. Администратор видит все. Параметры:
     * как у {@link #VISIBLE}, {@link #CREW} и {@link #TODO}.
     */
    private static final String SEEN = "(" + VISIBLE + "\n or " + CREW + "\n or " + TODO
            + "\n or repair_request.created_by_usr = :me)";

    /**
     * Время первого выполнения заявки; null - заявка не выполнялась. Параметр: done - статус RequestStatus.
     */
    private static final String FIRST_DONE = """
            (select min(done_status.dbeg) from repair_request_status done_status
             where done_status.repair_request = repair_request.id
               and done_status.request_status_type = :done)""";

    /**
     * Депо исполнителя, как на диаграмме «Заявки»: текущее депо того, кто был исполнителем в момент первого
     * выполнения. Параметры: depot и параметры {@link #FIRST_DONE}.
     */
    private static final String DONE_DEPOT = """
            exists (select 1 from repair_request_executor
                    join usr_depot on usr_depot.usr = repair_request_executor.executor_usr and usr_depot.dend is null
                    where repair_request_executor.repair_request = repair_request.id
                      and usr_depot.depot = :depot
                      and repair_request_executor.dbeg <= """ + FIRST_DONE + """

                      and (repair_request_executor.dend is null
                           or repair_request_executor.dend > """ + FIRST_DONE + "))";

    /**
     * Строка заявки: поля заявки, текущие статус, служба и исполнитель (repair_request_info), где вагон сейчас и
     * число фото. Где вагон: place - станция, депо или парк отстоя; wagon_station - станция места (станция,
     * станция депо или парка отстоя); wagon_trip - рейс, если вагон в рейсе. Параметр tripLocation -
     * LocationType.TRIP.
     */
    private static final String SELECT = """
            select repair_request.id, repair_request.number, repair_request.wagon, repair_request.trip,
                   repair_request.created_by_usr, repair_request.created_dt, repair_request.received_dt,
                   repair_request.fault_type, repair_request.severity_type, repair_request.description,
                   repair_request_info.request_status_type, repair_request_info.service,
                   repair_request_info.executor_usr,
                   wagon_info.location_type,
                   coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard) as place,
                   coalesce(wagon_info.station, depot.station, yard.station) as wagon_station,
                   case when wagon_info.location_type = :tripLocation then wagon_info.trip end as wagon_trip,
                   (select count(*) from repair_request_photo
                    where repair_request_photo.repair_request = repair_request.id) as photo_count
            """ + FROM;

    /**
     * Заявки по фильтру; если byStatus false - без отбора по статусам и критичности (для чисел у фильтров).
     *
     * @param me         сотрудник - для списков
     * @param visibility параметры условий {@link #TODO} и {@link #SEEN}, кроме me
     * @param finals     конечные статусы - для отбора открытых и закрытых
     * @param byStatus   отбирать по статусам и критичности фильтра
     */
    static SqlQuery list(RequestFilter filter, long me, Map<String, Object> visibility, List<Long> finals,
                         boolean byStatus, ListSort sort) {
        SqlWhere where = new SqlWhere();
        if ("mine".equals(filter.scope())) {
            where.add("repair_request.created_by_usr = :me", "me", me);
        }
        // «мне на исполнение»: ждёт действия лично сотрудника
        if ("todo".equals(filter.scope())) {
            where.add(TODO, "me", me);
            visibility.forEach(where::param);
        }
        // «все»: видные сотруднику
        if ("all".equals(filter.scope())) {
            where.add(SEEN, "me", me);
            visibility.forEach(where::param);
        }
        if (filter.closed() != null) {
            where.add((filter.closed() ? "" : "not ") + "repair_request_info.request_status_type = any(:finals)",
                    "finals", finals);
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            where.add("""
                    (strpos(lower(repair_request.number), lower(:query)) > 0
                     or strpos(wagon.number, :query) > 0
                     or repair_request.fault_type in (select fault_type.id from fault_type
                                                      where strpos(lower(fault_type.name), lower(:query)) > 0))""",
                    "query", filter.query().trim());
        }
        if (byStatus) {
            where.any("repair_request_info.request_status_type", "statuses", filter.statuses());
            where.any("repair_request.severity_type", "severities", filter.severities());
        }
        where.any("repair_request_info.service", "services", filter.services());
        where.any("repair_request.fault_type", "faultTypes", filter.faultTypes());
        if (filter.depot() != null) {
            where.add(DONE_DEPOT, "depot", filter.depot());
            where.param("done", RequestStatus.DONE);
        }
        if (filter.trip() != null) {
            where.add("repair_request.trip = :trip", "trip", filter.trip());
        }
        if (filter.wagon() != null) {
            where.add("repair_request.wagon = :wagon", "wagon", filter.wagon());
        }
        if (filter.from() != null) {
            where.add("repair_request.created_dt >= :from", "from", filter.from());
        }
        if (filter.to() != null) {
            where.add("repair_request.created_dt < :toNext", "toNext", filter.to().plusDays(1));
        }
        where.param("tripLocation", LocationType.TRIP);
        return where.query(SELECT, sort.orderBy(SORT, "repair_request.created_dt desc, repair_request.number desc"));
    }

    /**
     * Одна заявка. Параметры: id, tripLocation.
     */
    static String one() {
        return SELECT + "where repair_request.id = :id\n";
    }

    /**
     * Видна ли заявка сотруднику для исполнения ({@link #VISIBLE}): строка есть - видна. Параметры: id и параметры
     * {@link #VISIBLE}.
     */
    static String visible() {
        return "select repair_request.id\n" + FROM + "where repair_request.id = :id and " + VISIBLE + "\n";
    }

    /**
     * Видна ли заявка сотруднику ({@link #SEEN}): строка есть - видна. Параметры: id, me и параметры {@link #SEEN}.
     */
    static String seen() {
        return "select repair_request.id\n" + FROM + "where repair_request.id = :id and " + SEEN + "\n";
    }

    /**
     * Наибольший номер заявки с префиксом (код компании и год): номера одной длины, поэтому наибольший по строке -
     * последний. Параметр: prefix - «KZR-26-».
     */
    static String maxNumber() {
        return """
                select max(repair_request.number) as max_number
                from repair_request
                where strpos(repair_request.number, :prefix) = 1
                """;
    }

    /**
     * Блокировка выдачи номеров до конца транзакции: два сотрудника не получат один номер. Параметр: key.
     */
    static String lockNumbers() {
        return "select pg_advisory_xact_lock(:key) as locked";
    }

    /**
     * Смена статуса или исполнителя заявки позже момента: строка есть - была. Параметры: id, at.
     */
    static String changedAfter() {
        return """
                select repair_request_status.id from repair_request_status
                where repair_request_status.repair_request = :id and repair_request_status.dbeg > :at
                union all
                select repair_request_executor.id from repair_request_executor
                where repair_request_executor.repair_request = :id and repair_request_executor.dbeg > :at
                limit 1
                """;
    }

    /**
     * Журнал статусов заявки. Параметр: id.
     */
    static String statuses() {
        return "select dbeg, request_status_type, created_by_usr from repair_request_status where repair_request = :id";
    }

    /**
     * Журнал исполнителей заявки. Параметр: id.
     */
    static String executors() {
        return "select dbeg, executor_usr, created_by_usr from repair_request_executor where repair_request = :id";
    }

    /**
     * Примечания заявки. Параметр: id.
     */
    static String notes() {
        return """
                select id, created_dt, received_dt, created_by_usr, note
                from repair_request_note
                where repair_request = :id
                """;
    }

    /**
     * Чек-лист, по нарушению в котором создана заявка. Параметр: id. Колонки id, check_list_template; строк нет - заявка
     * не из чек-листа.
     */
    static String checkSource() {
        return """
                select check_list.id, check_list.check_list_template
                from check_list
                where check_list.id in (select check_list_value.check_list from check_list_value
                                        where check_list_value.repair_request = :id)
                limit 1
                """;
    }

    /**
     * Опись, по недостаче в которой создана заявка. Параметр: id. Колонки id, equip_list_template; строк нет - заявка
     * не из описи.
     */
    static String equipSource() {
        return """
                select equip_list.id, equip_list.equip_list_template
                from equip_list
                where equip_list.repair_request = :id
                limit 1
                """;
    }

    /**
     * Фото заявки. Параметр: id.
     */
    static String photos() {
        return """
                select id, created_dt, received_dt, created_by_usr
                from repair_request_photo
                where repair_request = :id
                """;
    }

    /**
     * Текущее состояние новой заявки (repair_request_info): в таблице нет колонки id, поэтому не Db.insertRec.
     * Параметры: repairRequest, dbegStatus, status, service, dbegExecutor, executorUsr.
     */
    static String insertInfo() {
        return """
                insert into repair_request_info (repair_request, dbeg_status, request_status_type, service,
                                                 dbeg_executor, executor_usr)
                values (:repairRequest, :dbegStatus, :status, :service, :dbegExecutor, :executorUsr)
                """;
    }

    /**
     * Сотрудники службы: не отключены, у одной из ролей - эта служба. Параметр: service.
     */
    static String serviceStaff() {
        return """
                select usr.id
                from usr
                where usr.disabled = false
                  and usr.id in (select usr_role.usr from usr_role
                                 join role_type on role_type.id = usr_role.role_type
                                 where role_type.service = :service)
                order by usr.full_name
                """;
    }

    /**
     * Бригада рейса - кому ЛНП назначает заявку: все сотрудники бригады; in_service - сотрудник службы заявки.
     * Параметры: service, trip.
     */
    static String crewStaff() {
        return """
                select trip_crew.usr, trip_crew.crew_duty_type,
                       trip_crew.usr in (select usr_role.usr from usr_role
                                         join role_type on role_type.id = usr_role.role_type
                                         where role_type.service = :service) as in_service
                from trip_crew
                join usr on usr.id = trip_crew.usr
                where trip_crew.trip = :trip and trip_crew.dend is null
                  and usr.disabled = false
                order by usr.full_name
                """;
    }

}
