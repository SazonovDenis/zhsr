package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.dto.ListSort;
import kz.zhsr.app.dto.RouteOption;
import kz.zhsr.app.dto.RouteFilter;
import kz.zhsr.app.dto.RouteRow;
import kz.zhsr.app.dto.TripOption;
import kz.zhsr.app.dto.TripFilter;
import kz.zhsr.app.dto.TripRow;
import kz.zhsr.app.dto.WagonOption;
import kz.zhsr.app.dto.WagonFilter;
import kz.zhsr.app.dto.WagonRow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Чтение списков для страниц «Вагоны», «Рейсы» и «Маршруты» и вариантов выбора для комбо. Вариант выбора - общая
 * часть строки: строка таблицы - вариант выбора и поля таблицы; оба собираются из одного запроса.
 * <p>
 * Методы только читают. Строка списка собирается одним SQL-запросом ({@link ListSql}): фильтр и сортировка - в БД. Значения
 * справочников отдаются id справочника, текст подставляет фронт (DictManager). Пустой список в строке - пустой
 * список, не null.
 * <p>
 * Пустое поле фильтра (null, пустая строка, пустой список) список не ограничивает. Поверх фильтра - видимость
 * по ролям сотрудника ({@link Visibility}): поле depot фильтра она может подменить депо сотрудника, а свой рейс и
 * свои вагоны открывает независимо от depot.
 */
@Service
public class ListService {

    /**
     * Состояние маршрута для фильтра: active - действует (dend пуст или не раньше сегодняшнего дня), closed - завершён.
     */
    public static final Set<String> ROUTE_STATES = Set.of("active", "closed");

    private final Db db;
    private final CurrentUserService currentUserService;
    private final Visibility visibility;
    private final RouteService routeService;

    public ListService(Db db, CurrentUserService currentUserService, Visibility visibility, RouteService routeService) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.visibility = visibility;
        this.routeService = routeService;
    }

    /**
     * Список вагонов (GET /wagons). По умолчанию - по номеру вагона.
     */
    public List<WagonRow> wagons(WagonFilter filter, ListSort sort) throws Exception {
        List<WagonRow> res = new ArrayList<>();
        for (DbRec r : wagonRecs(filter, sort)) {
            WagonRow row = fillWagonOption(new WagonRow(), r);
            row.setModelType(r.getLong("model_type", null));
            row.setDepot(r.getLong("depot", null));
            row.setDbegLocation(r.getDateTimeTZ("dbeg_location"));
            row.setWagonStatusType(r.getLong("wagon_status_type"));
            row.setDbegStatus(r.getDateTimeTZ("dbeg_status"));
            res.add(row);
        }
        return res;
    }

    /**
     * Вагоны как варианты выбора (GET /wagons/options): фильтр и видимость - как у списка вагонов.
     */
    public List<WagonOption> wagonOptions(WagonFilter filter, ListSort sort) throws Exception {
        return wagonRecs(filter, sort).stream().map(r -> fillWagonOption(new WagonOption(), r)).toList();
    }

    /**
     * Список рейсов (GET /trips). По умолчанию - по плановому отправлению.
     */
    public List<TripRow> trips(TripFilter filter, ListSort sort) throws Exception {
        // готовность рейса сервер пока не считает - пусто
        return tripRecs(filter, sort, r -> {
            TripRow row = new TripRow();
            row.setTransportType(r.getLong("transport_type"));
            row.setDepartedDt(r.getDateTimeTZ("departed_dt"));
            return row;
        });
    }

    /**
     * Рейсы как варианты выбора (GET /trips/options): фильтр и видимость - как у списка рейсов.
     */
    public List<TripOption> tripOptions(TripFilter filter, ListSort sort) throws Exception {
        return tripRecs(filter, sort, r -> new TripOption());
    }

    /**
     * Список маршрутов (GET /routes). По умолчанию - по номеру поезда.
     */
    public List<RouteRow> routes(RouteFilter filter, ListSort sort) throws Exception {
        return routeRecs(filter, sort, r -> {
            RouteRow row = new RouteRow();
            row.setTransportType(r.getLong("transport_type"));
            row.setDbeg(RouteService.localDate(r, "dbeg"));
            row.setDend(RouteService.localDate(r, "dend"));
            row.setConductorHours(RouteService.integer(r, "conductor_hours"));
            row.setLnpHours(RouteService.integer(r, "lnp_hours"));
            row.setPemHours(RouteService.integer(r, "pem_hours"));
            return row;
        });
    }

    /**
     * Маршруты как варианты выбора (GET /routes/options): фильтр и видимость - как у списка маршрутов.
     */
    public List<RouteOption> routeOptions(RouteFilter filter, ListSort sort) throws Exception {
        return routeRecs(filter, sort, r -> new RouteOption());
    }

    // --- общее для списка и вариантов выбора

    /**
     * Новый объект для строки запроса: вариант выбора или строка таблицы с её собственными полями; поля варианта выбора
     * заполняет вызывающий.
     */
    @FunctionalInterface
    private interface RowFactory<T> {
        T create(DbRec rec) throws Exception;
    }

    private List<DbRec> wagonRecs(WagonFilter filter, ListSort sort) throws Exception {
        Visibility.Access access = visibility.access(currentUserService.get(), filter.depot());
        SqlQuery q = ListSql.wagons(filter, access, sort);
        return db.loadSql(q.sql(), q.params());
    }

    private static <T extends WagonOption> T fillWagonOption(T option, DbRec r) {
        option.setId(r.getLong("id"));
        option.setNumber(r.getString("number"));
        option.setWagonType(r.getLong("wagon_type"));
        option.setLocationType(r.getLong("location_type"));
        option.setPlace(r.getLong("place"));
        option.setTrainNumber(r.getString("train_number"));
        option.setName(r.getString("name"));
        return option;
    }

    /**
     * Рейсы по фильтру и видимости; у каждого - вид, длина и остановки маршрута. Станции маршрутов - одним запросом на
     * все маршруты.
     */
    private <T extends TripOption> List<T> tripRecs(TripFilter filter, ListSort sort, RowFactory<T> factory)
            throws Exception {
        Visibility.Access access = visibility.access(currentUserService.get(), filter.depot());
        SqlQuery q = ListSql.trips(filter, access, sort);
        List<DbRec> trips = db.loadSql(q.sql(), q.params());
        if (trips.isEmpty()) {
            return List.of();
        }
        Set<Long> routes = new HashSet<>();
        for (DbRec trip : trips) {
            routes.add(trip.getLong("route"));
        }
        Map<Long, List<DbRec>> stations = routeService.stations(routes);
        List<T> res = new ArrayList<>(trips.size());
        for (DbRec trip : trips) {
            List<DbRec> points = stations.getOrDefault(trip.getLong("route"), List.of());
            T option = factory.create(trip);
            option.setId(trip.getLong("id"));
            option.setTrainNumber(trip.getString("train_number"));
            option.setRoute(trip.getLong("route"));
            option.setName(trip.getString("name"));
            option.setRouteKind(trip.getInt("route_kind"));
            option.setDuration(trip.getInt("duration"));
            option.setTripStatusType(trip.getLong("trip_status_type"));
            option.setPlannedDt(trip.getDateTimeTZ("planned_dt"));
            option.setKm(RouteService.km(points));
            option.setStops(RouteService.stops(points));
            res.add(option);
        }
        return res;
    }

    /**
     * Маршруты по фильтру и видимости; у каждого - длина и остановки. Станции маршрутов - одним запросом на все
     * маршруты.
     */
    private <T extends RouteOption> List<T> routeRecs(RouteFilter filter, ListSort sort, RowFactory<T> factory)
            throws Exception {
        Visibility.Access access = visibility.access(currentUserService.get(), filter.depot());

        List<String> states = filter.states() == null ? List.of() : filter.states();
        for (String state : states) {
            if (!ROUTE_STATES.contains(state)) {
                throw ApiException.badRequest("Неизвестное состояние маршрута: " + state);
            }
        }
        // выбраны оба состояния или ни одного - фильтра нет
        Boolean active = states.contains("active") == states.contains("closed") ? null : states.contains("active");

        SqlQuery q = ListSql.routes(filter, active, access, sort);
        List<DbRec> routes = db.loadSql(q.sql(), q.params());
        if (routes.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        for (DbRec r : routes) {
            ids.add(r.getLong("id"));
        }
        Map<Long, List<DbRec>> stations = routeService.stations(ids);
        List<T> res = new ArrayList<>(routes.size());
        for (DbRec r : routes) {
            List<DbRec> points = stations.getOrDefault(r.getLong("id"), List.of());
            T option = factory.create(r);
            option.setId(r.getLong("id"));
            option.setTrainNumber(r.getString("train_number"));
            option.setName(r.getString("name"));
            option.setRouteKind(r.getInt("route_kind"));
            option.setDuration(r.getInt("duration"));
            option.setKm(RouteService.km(points));
            option.setStops(RouteService.stops(points));
            res.add(option);
        }
        return res;
    }

}
