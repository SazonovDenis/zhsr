package kz.zhsr.app.service;

import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.TrainWagonChangeType;
import kz.zhsr.app.constants.TrainWagonKind;
import kz.zhsr.app.constants.TrainWagonPlanStatus;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.constants.WagonStatus;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.route.RouteFilter;
import kz.zhsr.app.dto.trip.TripFilter;
import kz.zhsr.app.dto.wagon.WagonFilter;

import java.util.Map;

/**
 * SQL списков {@link ListService}: текст запроса, условия фильтра и видимости, сортировка. Логики здесь нет - только
 * сборка запроса по уже проверенному фильтру.
 * <p>
 * Пустое поле фильтра (null, пустая строка, пустой список) условия не добавляет. Алиасов нет; алиас - только у
 * выражения.
 */
final class ListSql {

    private ListSql() {
    }

    // --- вагоны

    /**
     * Поле строки -> выражение сортировки.
     */
    private static final Map<String, String> WAGON_SORT = Map.of(
            "number", "wagon.number",
            "dbegLocation", "wagon_info.dbeg_location",
            "dbegStatus", "wagon_info.dbeg_status"
    );

    /**
     * Вагоны. Расположение: location_type и place - место по виду расположения (станция, депо, парк отстоя или рейс);
     * у вагона в рейсе - trip.
     *
     * @param limit не больше стольких строк; null - все
     */
    static SqlQuery wagons(WagonFilter filter, Visibility.Access access, ListSort sort, Integer limit) {
        SqlWhere where = new SqlWhere();
        where.visible(
                access.byDepot() ? (access.depot() == null ? "true" : "wagon.depot = :visDepot") : null,
                access.allTripWagons() ? "wagon.id in " + TripWagonSql.wagonsOf("visTrip") : null,
                access.wagons().isEmpty() ? null : "wagon.id = any(:visWagons)",
                access);
        if (access.allTripWagons()) {
            TripWagonSql.PARAMS.forEach(where::param);
        }
        where.any("wagon.id", "ids", filter.ids());
        if (filter.number() != null && !filter.number().isBlank()) {
            where.add("strpos(wagon.number, :number) > 0", "number", filter.number().trim());
        }
        boolean plan = Boolean.TRUE.equals(filter.plan()) && filter.at() != null;
        String with = "";
        if (plan) {
            PLAN_AT_PARAMS.forEach(where::param);
            where.param("at", filter.at());
            with = "with " + WAGON_AT_PLAN + "\n";
        }
        String place = plan ? "wagon_at" : "wagon_info";
        if (filter.station() != null) {
            where.add(plan ? """
                    ((wagon_at.location_type = :stationType and wagon_at.place = :station)
                     or (wagon_at.location_type = :depotType
                         and wagon_at.place in (select depot.id from depot where depot.station = :station))
                     or (wagon_at.location_type = :yardType
                         and wagon_at.place in (select yard.id from yard where yard.station = :station)))"""
                    : """
                    (wagon_info.station = :station
                     or wagon_info.depot in (select depot.id from depot where depot.station = :station)
                     or wagon_info.yard in (select yard.id from yard where yard.station = :station))""",
                    "station", filter.station());
        }
        if (filter.trip() != null) {
            where.add("wagon_info.trip = :trip", "trip", filter.trip());
        }
        where.any(place + ".location_type", "locationKinds", filter.locationKinds());
        where.any(place + ".wagon_status_type", "states", filter.states());

        return where.query(with + """
                select wagon.id, wagon.number, wagon.wagon_type, wagon.model_type, wagon.depot,
                       wagon_info.location_type,
                       coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard, wagon_info.trip) as place,
                       wagon_info.trip, wagon_info.dbeg_location, wagon_info.wagon_status_type,
                       wagon_info.dbeg_status, wagon_info.wagon_responsible,
                       wagon_info.run_km, wagon.initial_run_km, wagon.maker, wagon.build_year, wagon.dbeg, wagon.dend
                from wagon
                join wagon_info on wagon_info.wagon = wagon.id
                """ + (plan ? "join wagon_at on wagon_at.id = wagon.id\n" : ""), sort.orderBy(WAGON_SORT, "wagon.number") + (limit == null ? "" : "\nlimit " + limit));
    }

    /**
     * Вагоны поездов рейсов в статусе «план» и «выполняется» - второй проход списка вагонов: от рейсов (их немного,
     * индекс ix_trip_active), а не от вагонов. Вагон поезда рейса - {@link TripWagonSql#WHERE}. Параметры planned,
     * inProgress - состояния рейса и {@link TripWagonSql#PARAMS}.
     */
    static String activeTripWagons() {
        return """
                select distinct train_wagon.wagon, trip.id, trip.trip_status_type, trip.planned_dt
                from trip
                join train_wagon on train_wagon.train = trip.train
                where trip.trip_status_type in (:planned, :inProgress)
                  and %s
                order by trip.planned_dt
                """.formatted(TripWagonSql.WHERE);
    }

    /**
     * Поезд каждого вагона сейчас: открытая запись участия по факту - второй проход списка вагонов. Параметр fact -
     * слой «факт» участия.
     */
    static String currentTrains() {
        return """
                select train_wagon.wagon, train_wagon.train
                from train_wagon
                where train_wagon.kind = :fact and train_wagon.dend is null
                """;
    }

    /** Параметры {@link #WAGON_AT_PLAN} (кроме at и ids). */
    static final Map<String, Object> PLAN_AT_PARAMS = Map.of(
            "planKind", TrainWagonKind.PLAN,
            "waiting", TrainWagonPlanStatus.WAITING,
            "attach", TrainWagonChangeType.ATTACH,
            "detach", TrainWagonChangeType.DETACH,
            "stationType", LocationType.STATION,
            "depotType", LocationType.DEPOT,
            "yardType", LocationType.YARD,
            "tripType", LocationType.TRIP,
            "idle", WagonStatus.IN_IDLE,
            "inTrip", WagonStatus.IN_TRIP);

    /**
     * Расположение и состояние каждого вагона на момент at с учётом плана (CTE wagon_at): запись журнала факта,
     * действующая в момент, а если до момента есть ожидающая плановая отцепка или прицепка - её последний результат:
     * отцепка - вагон на станции отцепки в простое, прицепка - вагон в рейсе в поезде. Исполненный план уже в
     * журнале факта. Параметры at, {@link #PLAN_AT_PARAMS}.
     */
    private static final String WAGON_AT_PLAN = """
            plan_last as (
                select distinct on (train_wagon_change.wagon) train_wagon_change.wagon, train_wagon_change.change_type,
                       train_wagon_change.station, train_wagon_change.trip
                from train_wagon_change
                where train_wagon_change.kind = :planKind and train_wagon_change.plan_status = :waiting
                  and train_wagon_change.dt <= :at
                order by train_wagon_change.wagon, train_wagon_change.dt desc, train_wagon_change.id desc),
            wagon_at as (
                select wagon.id,
                       case plan_last.change_type when :detach then cast(:stationType as bigint)
                            when :attach then cast(:tripType as bigint) else wagon_location.location_type end
                            as location_type,
                       case plan_last.change_type when :detach then plan_last.station
                            when :attach then plan_last.trip
                            else coalesce(wagon_location.station, wagon_location.depot, wagon_location.yard,
                                          wagon_location.trip) end as place,
                       case plan_last.change_type when :detach then null when :attach then plan_last.trip
                            else wagon_location.trip end as trip,
                       case plan_last.change_type when :detach then cast(:idle as bigint)
                            when :attach then cast(:inTrip as bigint) else wagon_status.wagon_status_type end
                            as wagon_status_type
                from wagon
                left join plan_last on plan_last.wagon = wagon.id
                left join wagon_location on wagon_location.wagon = wagon.id
                     and tstzrange(wagon_location.dbeg, wagon_location.dend) @> :at
                left join wagon_status on wagon_status.wagon = wagon.id
                     and tstzrange(wagon_status.dbeg, wagon_status.dend) @> :at)
            """;

    /** Расположение и состояние на момент с учётом плана - второй проход списка вагонов. Параметры ids, at, PLAN_AT_PARAMS. */
    static String wagonsAtPlan() {
        return "with " + WAGON_AT_PLAN + "select id, location_type, place, trip, wagon_status_type from wagon_at where id = any(:ids)";
    }

    /**
     * Расположение вагонов на момент - второй проход списка вагонов при фильтре at: запись журнала, действующая в
     * момент (индекс ex_wagon_location_overlap). Параметры ids, at.
     */
    static String locationsAt() {
        return """
                select wagon_location.wagon, wagon_location.location_type,
                       coalesce(wagon_location.station, wagon_location.depot, wagon_location.yard,
                                wagon_location.trip) as place,
                       wagon_location.trip
                from wagon_location
                where wagon_location.wagon = any(:ids) and tstzrange(wagon_location.dbeg, wagon_location.dend) @> :at
                """;
    }

    /** Состояние вагонов на момент - как {@link #locationsAt()}. Параметры ids, at. */
    static String statusesAt() {
        return """
                select wagon_status.wagon, wagon_status.wagon_status_type
                from wagon_status
                where wagon_status.wagon = any(:ids) and tstzrange(wagon_status.dbeg, wagon_status.dend) @> :at
                """;
    }

    /**
     * Вагоны актов, ожидающих приёма, - второй проход списка вагонов: от ожидающих актов (индекс
     * ix_transfer_act_pending), а не от вагонов. Параметр pending - состояние акта.
     */
    static String pendingTransferWagons() {
        return """
                select transfer_act_wagon.wagon, transfer_act.id, transfer_act.number, transfer_act.to_station,
                       transfer_act.to_depot, transfer_act.to_yard
                from transfer_act
                join transfer_act_wagon on transfer_act_wagon.transfer_act = transfer_act.id
                where transfer_act.act_status_type = :pending
                """;
    }

    // --- рейсы

    private static final Map<String, String> TRIP_SORT = Map.of(
            "trainNumber", "route.train_number",
            "duration", RouteSql.DURATION,
            "plannedDt", "trip.planned_dt"
    );

    static SqlQuery trips(TripFilter filter, Visibility.Access access, ListSort sort) {
        SqlWhere where = new SqlWhere();
        where.visible(
                access.byDepot() ? (access.depot() == null ? "true" : routeViaDepot("trip.route")) : null,
                access.trip() == null ? null : "trip.id = any(:visTrips)",
                null,
                access);
        if (filter.id() != null) {
            where.add("trip.id = :id", "id", filter.id());
        }
        if (filter.route() != null) {
            where.add("trip.route = :route", "route", filter.route());
        }
        if (filter.station() != null) {
            where.add("""
                    exists (select 1 from route_station
                            where route_station.route = trip.route and route_station.station = :station)""",
                    "station", filter.station());
        }
        if (filter.wagon() != null && !filter.wagon().isBlank()) {
            where.add("""
                    exists (select 1 from train_wagon
                            join wagon on wagon.id = train_wagon.wagon
                            where train_wagon.train = trip.train and strpos(wagon.number, :wagon) > 0
                              and (train_wagon.dend is null
                                   or train_wagon.dend >= coalesce(trip.departed_dt, trip.planned_dt)))""",
                    "wagon", filter.wagon().trim());
        }
        if (filter.from() != null) {
            // рейс, идущий в периоде, виден и если отправился раньше from
            where.param("inProgress", TripStatus.IN_PROGRESS);
            where.add("(trip.trip_status_type = :inProgress"
                    + " or trip.planned_dt + make_interval(mins => " + RouteSql.DURATION + ") >= :from)", "from", filter.from());
        }
        if (filter.to() != null) {
            where.add("trip.planned_dt < :to", "to", filter.to());
        }
        where.any("trip.trip_status_type", "states", filter.states());

        return where.query("""
                select trip.id, route.train_number, trip.route, trip.train, route.name, route.transport_type,
                       route.route_kind,
                       %s as duration, trip.trip_status_type, trip.planned_dt, trip.departed_dt,
                       trip.arrived_dt, trip.departure_marked_dt, trip.arrival_marked_dt
                from trip
                join route on route.id = trip.route
                """.formatted(RouteSql.DURATION), sort.orderBy(TRIP_SORT, "trip.planned_dt, trip.id"));
    }

    // --- маршруты

    private static final Map<String, String> ROUTE_SORT = Map.of(
            "trainNumber", "route.train_number",
            "duration", RouteSql.DURATION
    );

    /**
     * @param active true - только действующие (dend пуст или не раньше сегодняшнего дня), false - только завершённые,
     *               null - все
     */
    static SqlQuery routes(RouteFilter filter, Boolean active, Visibility.Access access, ListSort sort) {
        SqlWhere where = new SqlWhere();
        where.visible(
                access.byDepot() ? (access.depot() == null ? "true" : routeViaDepot("route.id")) : null,
                access.trip() == null ? null : "route.id = (select trip.route from trip where trip.id = :visTrip)",
                null,
                access);
        where.any("route.route_kind", "kinds", filter.kinds());
        if (Boolean.TRUE.equals(active)) {
            where.add("(route.dend is null or route.dend >= current_date)");
        } else if (Boolean.FALSE.equals(active)) {
            where.add("route.dend < current_date");
        }

        return where.query("""
                select route.id, route.train_number, route.name, route.route_kind, route.transport_type, route.dbeg,
                       route.dend, %s as duration, route.conductor_hours, route.lnp_hours, route.pem_hours
                from route
                """.formatted(RouteSql.DURATION), sort.orderBy(ROUTE_SORT, "route.train_number"));
    }

    /**
     * Маршрут route идёт через станцию депо :visDepot.
     */
    private static String routeViaDepot(String route) {
        return """
                exists (select 1 from route_station join depot on depot.station = route_station.station
                        where route_station.route = %s and depot.id = :visDepot)""".formatted(route);
    }

    /**
     * Условия WHERE и их параметры.
     */

}
