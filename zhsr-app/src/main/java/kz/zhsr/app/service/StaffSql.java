package kz.zhsr.app.service;

import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.staff.StaffFilter;

import java.util.List;
import java.util.Map;

/**
 * SQL {@link StaffService}: запросы с соединениями и условиями, которые не покрываются методами Db. Параметры
 * запросов - в описании каждого метода.
 */
final class StaffSql {

    /** Сотрудник в бригаде рейса в статусе «выполняется». Параметр: inProgress. */
    private static final String IN_TRIP = """
            exists (select 1 from trip_crew join trip on trip.id = trip_crew.trip
                    where trip_crew.usr = usr.id and trip.trip_status_type = :inProgress)""";

    /**
     * Поле сортировки списка -> выражение SQL: ФИО, логин, статус («в рейсе» - после свободных), станция текущего депо.
     */
    private static final Map<String, String> SORT = Map.of(
            "fullName", "usr.full_name",
            "login", "usr.login",
            "factTrip", IN_TRIP,
            "station", "station.name"
    );

    private StaffSql() {
    }

    /**
     * Сотрудники по фильтру. Поля: id, full_name, login, disabled, depot, station, category_type - текущие
     * (незакрытые) записи usr_depot и usr_category.
     *
     * @param withDisabled показывать ли отключённых сотрудников
     * @param id           только этот сотрудник; null - все по фильтру
     */
    static SqlQuery list(StaffFilter filter, ListSort sort, boolean withDisabled, Long id) {
        SqlWhere where = new SqlWhere();
        where.param("inProgress", TripStatus.IN_PROGRESS);
        if (id != null) {
            where.add("usr.id = :id", "id", id);
        }
        if (!withDisabled) {
            where.add("usr.disabled = false");
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            where.add("(usr.full_name ilike :search or usr.login ilike :search or usr.phone ilike :search)",
                    "search", "%" + filter.search().trim()
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (filter.station() != null) {
            where.add("depot.station = :station", "station", filter.station());
        }
        if (filter.trip() != null) {
            where.add("exists (select 1 from trip_crew where trip_crew.usr = usr.id and trip_crew.trip = :trip)",
                    "trip", filter.trip());
        }
        if (filter.train() != null) {
            where.add("""
                    exists (select 1 from trip_crew join trip on trip.id = trip_crew.trip
                            where trip_crew.usr = usr.id and trip.train = :train and trip.trip_status_type = any(:active))""",
                    "train", filter.train());
            where.param("active", List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS));
        }
        return where.query("""
                select usr.id, usr.full_name, usr.login, usr.disabled, usr_depot.depot, depot.station,
                       usr_category.category_type
                from usr
                left join usr_depot on usr_depot.usr = usr.id and usr_depot.dend is null
                left join depot on depot.id = usr_depot.depot
                left join station on station.id = depot.station
                left join usr_category on usr_category.usr = usr.id and usr_category.dend is null
                """, sort.orderBy(SORT, "usr.full_name, usr.id"));
    }

    /**
     * Рейсы сотрудников в статусе «план» и «выполняется», по плановому отправлению. Поля: usr, trip, crew_duty_type,
     * trip_status_type. Параметры: usrs, states.
     */
    static String activeTrips() {
        return """
                select trip_crew.usr, trip_crew.trip, trip_crew.crew_duty_type, trip.trip_status_type
                from trip_crew
                join trip on trip.id = trip_crew.trip
                where trip_crew.usr = any(:usrs) and trip.trip_status_type = any(:states)
                order by trip.planned_dt, trip.id
                """;
    }

    /**
     * Вагоны, за которые сотрудники отвечают сейчас: текущий факт журнала ответственных. Поля: usr, wagon - по номеру
     * вагона. Параметры: usrs, fact.
     */
    static String factWagons() {
        return """
                select wagon_responsible_usr.usr, wagon_responsible.wagon
                from wagon_responsible
                join wagon_responsible_usr on wagon_responsible_usr.wagon_responsible = wagon_responsible.id
                join wagon on wagon.id = wagon_responsible.wagon
                where wagon_responsible_usr.usr = any(:usrs) and wagon_responsible.kind = :fact
                  and wagon_responsible.dend is null
                order by wagon.number
                """;
    }

    /**
     * Вагоны сотрудников по ожидающему плану ответственных на рейс (стадия - отправление) рейсов trips. Поля: usr,
     * trip, wagon - по номеру вагона. Параметры: usrs, trips, plan, waiting, departure.
     */
    static String planWagons() {
        return """
                select wagon_responsible_usr.usr, wagon_responsible.trip, wagon_responsible.wagon
                from wagon_responsible
                join wagon_responsible_usr on wagon_responsible_usr.wagon_responsible = wagon_responsible.id
                join wagon on wagon.id = wagon_responsible.wagon
                where wagon_responsible_usr.usr = any(:usrs) and wagon_responsible.trip = any(:trips)
                  and wagon_responsible.kind = :plan and wagon_responsible.plan_status = :waiting
                  and wagon_responsible.trip_point = :departure
                order by wagon.number
                """;
    }

}
