package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.repository.Db;
import kz.kis.kisdbtools.repository.DbRecMapper;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.RouteKind;
import kz.zhsr.app.constants.TransportType;
import kz.zhsr.app.dto.route.LegStation;
import kz.zhsr.app.dto.route.NextStation;
import kz.zhsr.app.dto.route.RecalcStops;
import kz.zhsr.app.dto.route.Route;
import kz.zhsr.app.dto.route.RouteDraft;
import kz.zhsr.app.dto.route.RouteLeg;
import kz.zhsr.app.dto.route.RoutePoint;
import kz.zhsr.app.dto.route.RouteSave;
import kz.zhsr.app.dto.route.RouteStop;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Маршруты (Ф-МАР-01, Ф-МАР-02): карточка со всеми станциями, создание, сохранение из редактора, станции для следующей
 * остановки и пересчёт остановок; станции маршрутов для строк списков. Путь между остановками - по маршрутной сети
 * ({@link RouteNetwork}). Создаёт и правит маршруты администратор или планировщик вагонов.
 */
@Service
public class RouteService {

    /**
     * Средняя скорость для времени в пути по умолчанию, км/ч (как в макете zhsr-web).
     */
    public static final int DEFAULT_SPEED_KMH = 70;

    /**
     * Наибольшая норма часов: колонки route.*_hours - numeric(6,2).
     */
    private static final double MAX_HOURS = 9999.99;

    private final Db db;
    private final CurrentUserService currentUserService;
    private final RouteNetwork network;

    public RouteService(Db db, CurrentUserService currentUserService, RouteNetwork network) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.network = network;
    }

    /**
     * Карточка маршрута (GET /routes/{id}): маршрут и все его станции по порядку.
     *
     * @throws ApiException 404 - маршрута нет
     */
    public Route route(long id) throws Exception {
        Route route = db.loadRec("route", id, Route.class);
        if (route == null) {
            throw ApiException.notFound("Маршрут не найден");
        }
        route.setPoints(DbRecMapper.toList(stations(List.of(id)).getOrDefault(id, List.of()), RoutePoint.class));
        return route;
    }

    /**
     * Удалить маршрут (DELETE /routes/{id}): администратор или планировщик вагонов. Остановки удаляются каскадом;
     * по маршруту есть рейсы - удалить не даст внешний ключ (сообщение - FkMessages).
     */
    @Transactional
    public void delete(long id) throws Exception {
        checkEditor();
        if (db.loadRec("route", id) == null) {
            throw ApiException.notFound("Маршрут не найден");
        }
        try {
            db.deleteRec("route", id);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    /**
     * Создать маршрут без остановок (Ф-МАР-01): остановки вводятся в редакторе. Вид перевозки - пассажирская.
     *
     * @throws ApiException 400 - не заполнены номер поезда, сообщение, вид или дата начала
     */
    @Transactional
    public Route create(RouteDraft draft) throws Exception {
        checkEditor();
        String train = trim(draft.trainNumber());
        String name = trim(draft.name());
        if (train.isEmpty() || name.isEmpty() || draft.routeKind() == null || draft.dbeg() == null) {
            throw ApiException.badRequest("Заполните номер поезда, сообщение, вид маршрута и дату ввода");
        }
        checkKind(draft.routeKind());
        Map<String, Object> rec = new HashMap<>();
        rec.put("train_number", train);
        rec.put("name", name);
        rec.put("route_kind", draft.routeKind());
        rec.put("transport_type", TransportType.PASSENGER);
        rec.put("dbeg", draft.dbeg());
        return route(db.insertRec("route", rec));
    }

    /**
     * Сохранить маршрут из редактора (Ф-МАР-01, Ф-МАР-02). Между соседними точками без перегона прокладывается
     * кратчайший путь по маршрутной сети - его станции становятся станциями следования; километраж - нарастающим итогом
     * по перегонам пути; время станций следования - по расстоянию между соседними остановками. Если по маршруту есть
     * рейсы, меняется только дата окончания действия.
     *
     * @throws ApiException 400 - маршрут не проходит проверки Ф-МАР-01; 404 - маршрута нет
     */
    @Transactional
    public Route save(long id, RouteSave body) throws Exception {
        checkEditor();
        Route current = route(id);
        if (body.dend() != null && body.dend().isAfter(LocalDate.now())) {
            throw ApiException.badRequest("Дата окончания действия - не больше сегодняшней");
        }

        if (!db.loadList("trip", Map.of("route", id)).isEmpty()) {
            if (!sameRoute(current, body)) {
                throw ApiException.badRequest("По маршруту есть рейсы: можно указать только дату окончания действия");
            }
            Map<String, Object> rec = new HashMap<>();
            rec.put("id", id);
            rec.put("dend", body.dend());
            db.updateRec("route", rec);
            return route(id);
        }

        String train = trim(body.trainNumber());
        if (train.isEmpty()) {
            throw ApiException.badRequest("Укажите номер поезда");
        }
        if (body.routeKind() == null || body.dbeg() == null) {
            throw ApiException.badRequest("Укажите вид маршрута и дату ввода");
        }
        checkKind(body.routeKind());
        for (Double h : new Double[]{body.conductorHours(), body.lnpHours(), body.pemHours()}) {
            if (h != null && (h < 0 || h > MAX_HOURS)) {
                throw ApiException.badRequest("Нормы часов - неотрицательные числа не больше " + MAX_HOURS);
            }
        }
        List<RoutePoint> points = buildPath(body.points());

        String name = trim(body.name());
        if (name.isEmpty()) {
            name = stopNames(points);
        }
        Map<String, Object> rec = new HashMap<>();
        rec.put("id", id);
        rec.put("train_number", train);
        rec.put("name", name);
        rec.put("route_kind", body.routeKind());
        rec.put("dbeg", body.dbeg());
        rec.put("dend", body.dend());
        rec.put("conductor_hours", body.conductorHours());
        rec.put("lnp_hours", body.lnpHours());
        rec.put("pem_hours", body.pemHours());
        db.updateRec("route", rec);

        db.deleteRec("route_station", Map.of("route", id));
        int seq = 1;
        for (RoutePoint p : points) {
            // время у начальной и конечной станций пусто - Map.of пустых значений не принимает
            Map<String, Object> station = new HashMap<>();
            station.put("route", id);
            station.put("seq", seq++);
            station.put("station", p.getStation());
            station.put("km", p.getKm());
            station.put("have_stop", p.isHaveStop());
            station.put("arrival_min", p.getArrivalMin());
            station.put("departure_min", p.getDepartureMin());
            db.insertRec("route_station", station);
        }
        return route(id);
    }

    /**
     * Станции, на которые можно перейти от остановки from: все достижимые по маршрутной сети, по числу перегонов от
     * from, затем по расстоянию; время в пути по умолчанию - по средней скорости. Без from - все станции
     * маршрутной сети без порядка: маршрут начинается с любой.
     */
    public List<NextStation> nextStations(Long station, Integer km) throws Exception {
        RouteNetwork.Graph graph = network.load();
        if (station == null) {
            return graph.edges().keySet().stream().map(h -> new NextStation(h, 0, 0, List.of(), 0)).toList();
        }
        int base = km == null ? 0 : km;
        return graph.paths(station).entrySet().stream()
                .filter(e -> e.getKey() != station.longValue())
                .sorted(Comparator.comparingInt((Map.Entry<Long, RouteNetwork.Path> e) -> e.getValue().stages())
                        .thenComparingInt(e -> e.getValue().km()))
                .map(e -> new NextStation(e.getKey(), base + e.getValue().km(), e.getValue().km(), e.getValue().via(),
                        defaultRunDuration(e.getValue().km())))
                .toList();
    }

    /**
     * Пересчитать остановки после правки в редакторе: расстояния от начала маршрута - у всех, по кратчайшему пути
     * между соседними остановками; время в пути от предыдущей остановки по умолчанию - только у остановок changed,
     * у остальных - прежнее. Время прибытия и отправления - заново от отправления с начальной станции: стоянки
     * прежние; у начальной станции нет прибытия, у конечной - отправления.
     *
     * @throws ApiException 400 - соседние остановки не связаны маршрутной сетью
     */
    public List<RouteStop> recalc(RecalcStops body) throws Exception {
        RouteNetwork.Graph graph = network.load();
        Set<Integer> changed = new HashSet<>(body.changed());
        List<RouteStop> stops = body.stops();
        List<RouteStop> res = new ArrayList<>(stops.size());
        int km = 0;
        // отправление с предыдущей остановки: до пересчёта и после
        Integer oldPrev = null;
        int prev = 0;
        for (int i = 0; i < stops.size(); i++) {
            RouteStop stop = stops.get(i);
            boolean last = i == stops.size() - 1;
            Integer arrival = null;
            Integer departure;
            if (i == 0) {
                departure = stop.departureMin() != null ? stop.departureMin()
                        : stop.arrivalMin() != null ? stop.arrivalMin() : 0;
            } else {
                int distance = distance(graph, stops.get(i - 1).station(), stop.station());
                km += distance;
                int run = !changed.contains(i) && stop.arrivalMin() != null && oldPrev != null
                        ? Math.max(0, stop.arrivalMin() - oldPrev) : defaultRunDuration(distance);
                arrival = prev + run;
                departure = last ? null : arrival + dwell(stop);
            }
            res.add(new RouteStop(stop.station(), km, arrival, departure));
            oldPrev = stop.departureMin() != null ? stop.departureMin() : stop.arrivalMin();
            prev = departure != null ? departure : arrival;
        }
        return res;
    }

    /**
     * Станции следования кратчайшего пути для каждой пары соседних остановок, в порядке legs; между станциями
     * перегон - пустой список. Редактор показывает их до сохранения маршрута.
     *
     * @throws ApiException 400 - станции пары не связаны маршрутной сетью
     */
    public List<List<LegStation>> legs(List<RouteLeg> legs) throws Exception {
        RouteNetwork.Graph graph = network.load();
        List<List<LegStation>> res = new ArrayList<>(legs.size());
        for (RouteLeg leg : legs) {
            Map<Long, RouteNetwork.Path> paths = graph.paths(leg.from());
            res.add(way(paths, leg.to()).via().stream()
                    .map(station -> new LegStation(station, paths.get(station).km()))
                    .toList());
        }
        return res;
    }

    /**
     * Стоянка остановки, мин: отправление минус прибытие; без одного из них - 0.
     */
    private static int dwell(RouteStop stop) {
        return stop.arrivalMin() != null && stop.departureMin() != null
                ? Math.max(0, stop.departureMin() - stop.arrivalMin()) : 0;
    }

    /**
     * Станции маршрутов (route_station) одним запросом на все маршруты.
     *
     * @return по id маршрута - его станции по порядку (seq); у маршрута без станций записи в карте нет
     */
    public Map<Long, List<DbRec>> stations(Collection<Long> routes) throws Exception {
        if (routes.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<DbRec>> res = UtDb.collectGroupBy_records(
                db.loadList("route_station", Map.of("route", routes)), "route");
        for (List<DbRec> list : res.values()) {
            list.sort(Comparator.comparingInt(rs -> rs.getInt("seq")));
        }
        return res;
    }

    /**
     * Остановки маршрута по порядку - id станций; станции следования не входят.
     */
    public static List<Long> stops(List<DbRec> stations) {
        return stations.stream()
                .filter(rs -> rs.getBoolean("have_stop"))
                .map(rs -> rs.getLong("station"))
                .toList();
    }

    /**
     * Длина маршрута, км: километраж последней станции; без станций - 0.
     */
    public static int km(List<DbRec> stations) {
        return stations.isEmpty() ? 0 : stations.get(stations.size() - 1).getInt("km");
    }

    /**
     * Путь маршрута по точкам редактора: между соседними точками без перегона - станции следования кратчайшего пути;
     * километраж - по перегонам; время станций следования - по расстоянию между соседними остановками. Проверки
     * Ф-МАР-01: не меньше двух остановок, начальная и конечная - остановки, одна станция дважды
     * подряд - нельзя; время остановок задано и не убывает.
     *
     * @throws ApiException 400 - путь не проходит проверки
     */
    private List<RoutePoint> buildPath(List<RoutePoint> input) throws Exception {
        if (input.stream().filter(RoutePoint::isHaveStop).count() < 2) {
            throw ApiException.badRequest("У маршрута должно быть не меньше двух остановок");
        }
        checkTimes(input.stream().filter(RoutePoint::isHaveStop).toList());
        RoutePoint first = input.get(0);
        RoutePoint last = input.get(input.size() - 1);
        RouteNetwork.Graph graph = network.load();
        if (!first.isHaveStop() || !last.isHaveStop()) {
            throw ApiException.badRequest("Начальная и конечная станции маршрута должны быть остановками");
        }

        // точки с проложенными путями; время станций следования - после, по расстоянию
        List<RoutePoint> path = new ArrayList<>();
        int km = 0;
        for (int i = 0; i < input.size(); i++) {
            RoutePoint p = input.get(i);
            if (i == 0) {
                path.add(new RoutePoint(p.getStation(), 0, true, null, p.getDepartureMin()));
                continue;
            }
            long prev = input.get(i - 1).getStation();
            if (prev == p.getStation()) {
                throw ApiException.badRequest("Одна станция дважды подряд - между ними нужны станции следования");
            }
            Integer stage = graph.stage(prev, p.getStation());
            if (stage == null) {
                RouteNetwork.Path way = graph.paths(prev).get(p.getStation());
                if (way == null) {
                    throw ApiException.badRequest("Станции маршрута не связаны маршрутной сетью");
                }
                long at = prev;
                for (long via : way.via()) {
                    km += graph.stage(at, via);
                    path.add(new RoutePoint(via, km, false, null, null));
                    at = via;
                }
                stage = graph.stage(at, p.getStation());
            }
            km += stage;
            boolean lastPoint = i == input.size() - 1;
            path.add(p.isHaveStop()
                    ? new RoutePoint(p.getStation(), km, true, p.getArrivalMin(), lastPoint ? null : p.getDepartureMin())
                    : new RoutePoint(p.getStation(), km, false, null, null));
        }
        return spreadTimes(path);
    }

    /**
     * Время остановок: у начальной - отправление, у конечной - прибытие, у промежуточных - оба; прибытие не раньше
     * отправления с предыдущей остановки, отправление не раньше прибытия.
     *
     * @throws ApiException 400 - время не задано или убывает
     */
    private static void checkTimes(List<RoutePoint> stops) {
        int prev = -1;
        for (int i = 0; i < stops.size(); i++) {
            RoutePoint p = stops.get(i);
            boolean first = i == 0;
            boolean last = i == stops.size() - 1;
            if ((!first && p.getArrivalMin() == null) || (!last && p.getDepartureMin() == null)) {
                throw ApiException.badRequest("Укажите время прибытия и отправления каждой остановки");
            }
            for (Integer time : new Integer[]{first ? null : p.getArrivalMin(), last ? null : p.getDepartureMin()}) {
                if (time == null) {
                    continue;
                }
                if (time < prev) {
                    throw ApiException.badRequest("Время прибытия и отправления по остановкам не должно убывать");
                }
                prev = time;
            }
        }
    }

    /**
     * Время станций следования: между отправлением с предыдущей остановки и прибытием на следующую - по расстоянию;
     * прибытие и отправление станции следования совпадают.
     */
    private static List<RoutePoint> spreadTimes(List<RoutePoint> path) {
        List<RoutePoint> res = new ArrayList<>(path);
        int start = 0;
        for (int i = 1; i < path.size(); i++) {
            if (!path.get(i).isHaveStop()) {
                continue;
            }
            RoutePoint from = path.get(start);
            RoutePoint to = path.get(i);
            int length = to.getKm() - from.getKm();
            int run = to.getArrivalMin() - from.getDepartureMin();
            for (int j = start + 1; j < i; j++) {
                RoutePoint p = path.get(j);
                int time = from.getDepartureMin()
                        + (length > 0 ? Math.round((float) run * (p.getKm() - from.getKm()) / length) : 0);
                res.set(j, new RoutePoint(p.getStation(), p.getKm(), false, time, time));
            }
            start = i;
        }
        return res;
    }

    /**
     * Расстояние между станциями по кратчайшему пути, км.
     *
     * @throws ApiException 400 - станции не связаны маршрутной сетью
     */
    private static int distance(RouteNetwork.Graph graph, long from, long to) {
        return way(graph.paths(from), to).km();
    }

    /**
     * Путь до станции to.
     *
     * @throws ApiException 400 - станции не связаны маршрутной сетью
     */
    private static RouteNetwork.Path way(Map<Long, RouteNetwork.Path> paths, long to) {
        RouteNetwork.Path way = paths.get(to);
        if (way == null) {
            throw ApiException.badRequest("Станции маршрута не связаны маршрутной сетью");
        }
        return way;
    }

    private static int defaultRunDuration(int km) {
        return Math.round(km * 60f / DEFAULT_SPEED_KMH);
    }

    /**
     * Сообщение из названий остановок через тире (station.name).
     */
    private String stopNames(List<RoutePoint> points) throws Exception {
        List<Long> stops = points.stream().filter(RoutePoint::isHaveStop).map(RoutePoint::getStation).toList();
        Map<Long, Object> names = UtDb.flatLong(
                db.loadList("station", Map.of("id", new HashSet<>(stops))), "id", "name");
        return String.join(" — ", stops.stream().map(s -> (String) names.get(s)).toList());
    }

    /**
     * Атрибуты и остановки маршрута не изменились - изменилась разве что дата окончания.
     */
    private static boolean sameRoute(Route r, RouteSave body) {
        return Objects.equals(trim(body.trainNumber()), r.getTrainNumber())
                && Objects.equals(trim(body.name()), r.getName())
                && Objects.equals(body.routeKind(), r.getRouteKind())
                && Objects.equals(body.dbeg(), r.getDbeg())
                && Objects.equals(body.conductorHours(), r.getConductorHours())
                && Objects.equals(body.lnpHours(), r.getLnpHours())
                && Objects.equals(body.pemHours(), r.getPemHours())
                && stopsOf(body.points()).equals(stopsOf(r.getPoints()));
    }

    /**
     * Остановки по порядку: станция, время прибытия и отправления - для сравнения маршрутов.
     */
    private static List<List<Object>> stopsOf(List<RoutePoint> points) {
        return points.stream().filter(RoutePoint::isHaveStop)
                .map(p -> Arrays.<Object>asList(p.getStation(), p.getArrivalMin(), p.getDepartureMin())).toList();
    }

    /**
     * Создавать и править маршруты может администратор или планировщик вагонов (Ф-МАР-01).
     *
     * @throws ApiException 403 - не может
     */
    private void checkEditor() {
        CurrentUser user = currentUserService.get();
        if (!user.hasRole(RoleType.ADMIN) && !user.hasRole(RoleType.WAGON_PLANNER)) {
            throw ApiException.forbidden("Создавать и править маршруты может администратор или планировщик вагонов");
        }
    }

    private static void checkKind(int kind) {
        if (kind != RouteKind.REGULAR && kind != RouteKind.ONE_TIME) {
            throw ApiException.badRequest("Неизвестный вид маршрута");
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    static LocalDate localDate(DbRec r, String field) {
        return r.get(field) instanceof LocalDate d ? d : null;
    }

}
