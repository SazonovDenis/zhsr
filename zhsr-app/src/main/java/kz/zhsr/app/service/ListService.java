package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.ActStatus;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.route.RouteFilter;
import kz.zhsr.app.dto.route.RouteOption;
import kz.zhsr.app.dto.route.RouteRow;
import kz.zhsr.app.dto.trip.TripFilter;
import kz.zhsr.app.dto.trip.TripOption;
import kz.zhsr.app.dto.trip.TripRow;
import kz.zhsr.app.dto.wagon.WagonFilter;
import kz.zhsr.app.dto.wagon.WagonOption;
import kz.zhsr.app.dto.wagon.WagonPendingTransfer;
import kz.zhsr.app.dto.wagon.WagonRow;
import kz.zhsr.app.dto.wagon.WagonPlace;
import kz.zhsr.app.dto.wagon.WagonTrip;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Чтение списков для страниц «Вагоны», «Рейсы» и «Маршруты» и вариантов выбора для комбо. Вариант выбора - общая
 * часть строки: строка таблицы - вариант выбора и поля таблицы; оба собираются из одного запроса.
 * <p>
 * Методы только читают. Строка списка читается одним SQL-запросом ({@link ListSql}): фильтр и сортировка - в БД. Значения
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
    private final RefResolver refResolver;

    public ListService(Db db, CurrentUserService currentUserService, Visibility visibility, RouteService routeService,
                       RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.visibility = visibility;
        this.routeService = routeService;
        this.refResolver = refResolver;
    }

    /**
     * Список вагонов (GET /wagons). По умолчанию - по номеру вагона.
     */
    public List<WagonRow> wagons(WagonFilter filter, ListSort sort) throws Exception {
        List<WagonRow> rows = wagonRows(filter, sort, WagonRow.class);
        addWagonTripsAndTransfers(rows);
        if (filter.at() != null) {
            addWagonStateAt(rows, filter.at());
        }
        return rows;
    }

    /**
     * Второй проход списка вагонов при фильтре at: расположение и состояние на момент по журналам - дополнительно к
     * текущим.
     */
    private void addWagonStateAt(List<WagonRow> rows, OffsetDateTime at) throws Exception {
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, WagonRow> byId = new HashMap<>();
        for (WagonRow row : rows) {
            byId.put(row.getId(), row);
        }
        Map<String, Object> params = Map.of("ids", List.copyOf(byId.keySet()), "at", at);
        for (DbRec rec : db.loadSql(ListSql.locationsAt(), params)) {
            WagonPlace place = new WagonPlace();
            place.setLocationType(rec.getLong("location_type"));
            place.setPlace(rec.getLong("place"));
            Long trip = rec.getLong("trip", null);
            place.setTrip(trip == null ? null : new TripRef(trip));
            byId.get(rec.getLong("wagon")).setLocationAt(place);
        }
        for (DbRec rec : db.loadSql(ListSql.statusesAt(), params)) {
            byId.get(rec.getLong("wagon")).setWagonStatusTypeAt(rec.getLong("wagon_status_type"));
        }
        refResolver.resolve(rows);
    }

    /**
     * Второй проход списка вагонов: рейсы «план» и «выполняется», в составе которых вагон, и акт, ожидающий приёма.
     * Грузятся от рейсов и актов (их немного), без списка вагонов, и раскладываются по строкам.
     */
    private void addWagonTripsAndTransfers(List<WagonRow> rows) throws Exception {
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, WagonRow> byId = new HashMap<>();
        for (WagonRow row : rows) {
            byId.put(row.getId(), row);
        }
        List<DbRec> trips = db.loadSql(ListSql.activeTripWagons(),
                Map.of("planned", TripStatus.PLANNED, "inProgress", TripStatus.IN_PROGRESS));
        for (DbRec rec : trips) {
            WagonRow row = byId.get(rec.getLong("wagon"));
            if (row != null) {
                WagonTrip trip = new WagonTrip();
                trip.setTrip(new TripRef(rec.getLong("trip")));
                trip.setTripStatusType(rec.getLong("trip_status_type"));
                row.getTrips().add(trip);
            }
        }
        List<DbRec> acts = db.loadSql(ListSql.pendingTransferWagons(), Map.of("pending", ActStatus.PENDING));
        for (DbRec rec : acts) {
            WagonRow row = byId.get(rec.getLong("wagon"));
            if (row != null) {
                WagonPendingTransfer act = new WagonPendingTransfer();
                act.setId(rec.getLong("id"));
                act.setNumber(rec.getString("number"));
                act.setToStation(rec.getLong("to_station", null));
                act.setToDepot(rec.getLong("to_depot", null));
                act.setToYard(rec.getLong("to_yard", null));
                row.setPendingTransfer(act);
            }
        }
        refResolver.resolve(rows);
    }

    /**
     * Вагоны как варианты выбора (GET /wagons/options): фильтр и видимость - как у списка вагонов.
     */
    public List<WagonOption> wagonOptions(WagonFilter filter, ListSort sort) throws Exception {
        return wagonRows(filter, sort, WagonOption.class);
    }

    /**
     * Список рейсов (GET /trips). По умолчанию - по плановому отправлению.
     */
    public List<TripRow> trips(TripFilter filter, ListSort sort) throws Exception {
        return tripRows(filter, sort, TripRow.class);
    }

    /**
     * Рейсы как варианты выбора (GET /trips/options): фильтр и видимость - как у списка рейсов.
     */
    public List<TripOption> tripOptions(TripFilter filter, ListSort sort) throws Exception {
        return tripRows(filter, sort, TripOption.class);
    }

    /**
     * Список маршрутов (GET /routes). По умолчанию - по номеру поезда.
     */
    public List<RouteRow> routes(RouteFilter filter, ListSort sort) throws Exception {
        return routeRows(filter, sort, RouteRow.class);
    }

    /**
     * Маршруты как варианты выбора (GET /routes/options): фильтр и видимость - как у списка маршрутов.
     */
    public List<RouteOption> routeOptions(RouteFilter filter, ListSort sort) throws Exception {
        return routeRows(filter, sort, RouteOption.class);
    }

    // --- общее для списка и вариантов выбора

    private <T extends WagonOption> List<T> wagonRows(WagonFilter filter, ListSort sort, Class<T> clazz) throws Exception {
        Visibility.Access access = visibility.access(currentUserService.get(), filter.depot());
        // поиск по номеру отменяет все фильтры и отбор по роли: ищется среди всех вагонов (Ф-ВАГ-04)
        if (filter.number() != null && !filter.number().isBlank()) {
            filter = new WagonFilter(null, filter.number(), null, null, null, null, filter.at());
            access = new Visibility.Access(true, null, null, false, null, null);
        }
        SqlQuery q = ListSql.wagons(filter, access, sort);
        List<T> res = db.loadSql(q.sql(), q.params(), clazz);
        refResolver.resolve(res);
        return res;
    }

    /**
     * Рейсы по фильтру и видимости; у каждого - вид, длина и остановки маршрута. Станции маршрутов - одним запросом на
     * все маршруты.
     */
    private <T extends TripOption> List<T> tripRows(TripFilter filter, ListSort sort, Class<T> clazz) throws Exception {
        Visibility.Access access = visibility.access(currentUserService.get(), filter.depot());
        SqlQuery q = ListSql.trips(filter, access, sort);
        List<T> trips = db.loadSql(q.sql(), q.params(), clazz);
        if (trips.isEmpty()) {
            return trips;
        }
        Set<Long> routes = trips.stream().map(TripOption::getRoute).collect(Collectors.toSet());
        Map<Long, List<DbRec>> stations = routeService.stations(routes);
        for (T trip : trips) {
            List<DbRec> points = stations.getOrDefault(trip.getRoute(), List.of());
            trip.setKm(RouteService.km(points));
            trip.setStops(RouteService.stops(points));
        }
        return trips;
    }

    /**
     * Маршруты по фильтру и видимости; у каждого - длина и остановки. Станции маршрутов - одним запросом на все
     * маршруты.
     */
    private <T extends RouteOption> List<T> routeRows(RouteFilter filter, ListSort sort, Class<T> clazz)
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
        List<T> routes = db.loadSql(q.sql(), q.params(), clazz);
        if (routes.isEmpty()) {
            return routes;
        }
        Set<Long> ids = routes.stream().map(RouteOption::getId).collect(Collectors.toSet());
        Map<Long, List<DbRec>> stations = routeService.stations(ids);
        for (T route : routes) {
            List<DbRec> points = stations.getOrDefault(route.getId(), List.of());
            route.setKm(RouteService.km(points));
            route.setStops(RouteService.stops(points));
        }
        return routes;
    }

}
