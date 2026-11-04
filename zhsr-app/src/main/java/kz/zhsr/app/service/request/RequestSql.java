package kz.zhsr.app.service.request;

import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.dto.ListSort;
import kz.zhsr.app.dto.RequestFilter;
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
     * Строка заявки: поля заявки, текущие статус, служба и исполнитель (repair_request_info), тип вагона, где вагон сейчас и
     * число фото. Где вагон: wagon_place - станция, депо или парк отстоя; wagon_station - станция места (станция,
     * станция депо или парка отстоя); wagon_trip - рейс, если вагон в рейсе. Параметр tripLocation -
     * LocationType.TRIP.
     */
    private static final String SELECT = """
            select repair_request.id, repair_request.number, repair_request.wagon, repair_request.trip,
                   repair_request.created_by_usr, repair_request.created_dt, repair_request.fault_type,
                   repair_request.severity_type, repair_request.description,
                   repair_request_info.request_status_type, repair_request_info.service,
                   repair_request_info.executor_usr,
                   wagon.wagon_type, wagon_info.location_type,
                   coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard) as wagon_place,
                   coalesce(wagon_info.station, depot.station, yard.station) as wagon_station,
                   case when wagon_info.location_type = :tripLocation then wagon_info.trip end as wagon_trip,
                   (select count(*) from repair_request_photo
                    where repair_request_photo.repair_request = repair_request.id) as photo_count
            """ + FROM;

    /**
     * Заявки по фильтру; если byStatus false - без отбора по статусам и критичности (для чисел у фильтров).
     *
     * @param me         сотрудник - для списков «мои» и «на исполнение»
     * @param visibility параметры условия {@link #VISIBLE} - для списка «на исполнение»
     * @param finals     конечные статусы - для отбора открытых и закрытых
     * @param byStatus   отбирать по статусам и критичности фильтра
     */
    static SqlQuery list(RequestFilter filter, long me, Map<String, Object> visibility, List<Long> finals,
                         boolean byStatus, ListSort sort) {
        SqlWhere where = new SqlWhere();
        if ("mine".equals(filter.scope())) {
            where.add("repair_request.created_by_usr = :me", "me", me);
        }
        // «на исполнение»: назначена лично или видна для исполнения
        if ("todo".equals(filter.scope())) {
            where.add("(repair_request_info.executor_usr = :me or " + VISIBLE + ")", "me", me);
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
        if (filter.trip() != null) {
            where.add("repair_request.trip = :trip", "trip", filter.trip());
        }
        if (filter.from() != null) {
            where.add("repair_request.created_dt >= :from", "from", filter.from());
        }
        if (filter.to() != null) {
            where.add("repair_request.created_dt < :toNext", "toNext", filter.to().plusDays(1));
        }
        where.param("tripLocation", LocationType.TRIP);
        return where.query(SELECT, sort.orderBy(SORT, "repair_request.created_dt desc, repair_request.id desc"));
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
     * Наибольший номер заявки с префиксом (код компании и год): номера одной длины, поэтому наибольший по строке -
     * последний. Параметр: prefix - «KZR-26-%».
     */
    static String maxNumber() {
        return """
                select max(repair_request.number) as max_number
                from repair_request
                where repair_request.number like :prefix
                """;
    }

    /**
     * Блокировка выдачи номеров до конца транзакции: два сотрудника не получат один номер. Параметр: key.
     */
    static String lockNumbers() {
        return "select pg_advisory_xact_lock(:key) as locked";
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
                select usr.id, usr.full_name
                from usr
                where usr.disabled = false
                  and usr.id in (select usr_role.usr from usr_role
                                 join role_type on role_type.id = usr_role.role_type
                                 where role_type.service = :service)
                order by usr.full_name
                """;
    }

}
