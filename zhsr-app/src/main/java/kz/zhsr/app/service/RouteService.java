package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.RouteKind;
import kz.zhsr.app.constants.TransportType;
import kz.zhsr.app.dto.NextStation;
import kz.zhsr.app.dto.RecalcStops;
import kz.zhsr.app.dto.Route;
import kz.zhsr.app.dto.RouteDraft;
import kz.zhsr.app.dto.RoutePoint;
import kz.zhsr.app.dto.RouteSave;
import kz.zhsr.app.dto.RouteStop;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
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
 * ({@link RouteNetwork}). Создаёт и правит маршруты администратор или планировщик.
 */
@Service
public class RouteService {

    /**
     * Средняя скорость для времени в пути по умолчанию, км/ч (как в макете zhsr-web).
     */
    public static final int DEFAULT_SPEED_KMH = 70;

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
        DbRec route = db.loadRec("route", id);
        if (route == null) {
            throw ApiException.notFound("Маршрут не найден");
        }
        List<RoutePoint> points = stations(List.of(id)).getOrDefault(id, List.of()).stream()
                .map(rs -> new RoutePoint(rs.getLong("station"), rs.getInt("km"), rs.getBoolean("have_stop"),
                        rs.getInt("run_duration"), rs.getInt("stop_duration")))
                .toList();
        return new Route(route.getLong("id"), route.getString("train_number"), route.getString("name"),
                route.getInt("route_kind"), route.getLong("transport_type"), localDate(route, "dbeg"),
                localDate(route, "dend"), route.getInt("duration"), integer(route, "conductor_hours"),
                integer(route, "lnp_hours"), integer(route, "pem_hours"), points);
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
        rec.put("duration", 0);
        return route(db.insertRec("route", rec));
    }

    /**
     * Сохранить маршрут из редактора (Ф-МАР-01, Ф-МАР-02). Между соседними точками без перегона прокладывается
     * кратчайший путь по маршрутной сети - его станции становятся станциями следования; километраж - нарастающим итогом
     * по перегонам пути. Если по маршруту есть рейсы, меняется только дата окончания действия.
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
        if (body.duration() == null || body.duration() <= 0) {
            throw ApiException.badRequest("Укажите время в пути");
        }
        for (Integer h : new Integer[]{body.conductorHours(), body.lnpHours(), body.pemHours()}) {
            if (h != null && h < 0) {
                throw ApiException.badRequest("Нормы часов - целые неотрицательные числа");
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
        rec.put("duration", body.duration());
        rec.put("conductor_hours", body.conductorHours());
        rec.put("lnp_hours", body.lnpHours());
        rec.put("pem_hours", body.pemHours());
        db.updateRec("route", rec);

        db.deleteRec("route_station", Map.of("route", id));
        int seq = 1;
        for (RoutePoint p : points) {
            db.insertRec("route_station", Map.of("route", id, "seq", seq++, "station", p.station(), "km", p.km(),
                    "have_stop", p.haveStop(), "run_duration", p.runDuration(), "stop_duration", p.stopDuration()));
        }
        return route(id);
    }

    /**
     * Станции, на которые можно перейти от остановки from: все достижимые по маршрутной сети, по числу перегонов от
     * from, затем по расстоянию; время в пути по умолчанию - по средней скорости. Без from - узловые станции: с них
     * начинается маршрут.
     */
    public List<NextStation> nextStations(Long station, Integer km) throws Exception {
        RouteNetwork.Graph graph = network.load();
        if (station == null) {
            return graph.hubs().stream().sorted().map(h -> new NextStation(h, 0, 0, List.of(), 0)).toList();
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
     * между соседними остановками; время в пути по умолчанию - только у остановок changed.
     *
     * @throws ApiException 400 - соседние остановки не связаны маршрутной сетью
     */
    public List<RouteStop> recalc(RecalcStops body) throws Exception {
        RouteNetwork.Graph graph = network.load();
        Set<Integer> changed = new HashSet<>(body.changed());
        List<RouteStop> res = new ArrayList<>(body.stops().size());
        int km = 0;
        for (int i = 0; i < body.stops().size(); i++) {
            RouteStop stop = body.stops().get(i);
            int distance = 0;
            if (i > 0) {
                distance = distance(graph, body.stops().get(i - 1).station(), stop.station());
                km += distance;
            }
            int run = i == 0 ? 0 : changed.contains(i) ? defaultRunDuration(distance) : stop.runDuration();
            res.add(new RouteStop(stop.station(), km, run, stop.stopDuration()));
        }
        return res;
    }

    /**
     * Станции маршрутов (route_station) одним запросом на все маршруты.
     *
     * @return по id маршрута - его станции по порядку (seq); у маршрута без станций записи в карте нет
     */
    public Map<Long, List<DbRec>> stations(Collection<Long> routes) throws Exception {
        Map<Long, List<DbRec>> res = new HashMap<>();
        if (routes.isEmpty()) {
            return res;
        }
        for (DbRec rs : db.loadList("route_station", Map.of("route", routes))) {
            res.computeIfAbsent(rs.getLong("route"), k -> new ArrayList<>()).add(rs);
        }
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
     * километраж - по перегонам; время в пути участка между остановками, где добавлены станции, делится по расстоянию.
     * Проверки Ф-МАР-01: не меньше двух остановок, начальная и конечная - остановки на узловых станциях, одна станция
     * дважды подряд - нельзя.
     *
     * @throws ApiException 400 - путь не проходит проверки
     */
    private List<RoutePoint> buildPath(List<RoutePoint> input) throws Exception {
        if (input.stream().filter(RoutePoint::haveStop).count() < 2) {
            throw ApiException.badRequest("У маршрута должно быть не меньше двух остановок");
        }
        RoutePoint first = input.get(0);
        RoutePoint last = input.get(input.size() - 1);
        RouteNetwork.Graph graph = network.load();
        if (!first.haveStop() || !last.haveStop() || !graph.hubs().contains(first.station())
                || !graph.hubs().contains(last.station())) {
            throw ApiException.badRequest("Начальная и конечная станции маршрута - только узловые");
        }

        // точки с проложенными путями; added - участок между остановками получил новые станции
        List<RoutePoint> path = new ArrayList<>();
        List<Boolean> added = new ArrayList<>();
        int km = 0;
        for (int i = 0; i < input.size(); i++) {
            RoutePoint p = input.get(i);
            if (i == 0) {
                path.add(new RoutePoint(p.station(), 0, true, 0, 0));
                added.add(false);
                continue;
            }
            long prev = input.get(i - 1).station();
            if (prev == p.station()) {
                throw ApiException.badRequest("Одна станция дважды подряд - между ними нужны станции следования");
            }
            Integer stage = graph.stage(prev, p.station());
            if (stage == null) {
                RouteNetwork.Path way = graph.paths(prev).get(p.station());
                if (way == null) {
                    throw ApiException.badRequest("Станции маршрута не связаны маршрутной сетью");
                }
                long at = prev;
                for (long via : way.via()) {
                    km += graph.stage(at, via);
                    path.add(new RoutePoint(via, km, false, 0, 0));
                    added.add(true);
                    at = via;
                }
                stage = graph.stage(at, p.station());
            }
            km += stage;
            boolean lastPoint = i == input.size() - 1;
            int stopDuration = p.haveStop() && !lastPoint ? p.stopDuration() : 0;
            path.add(new RoutePoint(p.station(), km, p.haveStop(), p.runDuration(), stopDuration));
            added.add(false);
        }
        return spreadRunDuration(path, added);
    }

    /**
     * Время в пути участка между остановками, где добавлены станции следования, - заново по расстоянию: сумма времени
     * участка сохраняется.
     */
    private static List<RoutePoint> spreadRunDuration(List<RoutePoint> path, List<Boolean> added) {
        List<RoutePoint> res = new ArrayList<>(path);
        int start = 0;
        for (int i = 1; i < path.size(); i++) {
            if (!path.get(i).haveStop()) {
                continue;
            }
            boolean hasAdded = false;
            int total = 0;
            for (int j = start + 1; j <= i; j++) {
                hasAdded |= added.get(j);
                total += path.get(j).runDuration();
            }
            if (hasAdded) {
                int length = path.get(i).km() - path.get(start).km();
                int used = 0;
                for (int j = start + 1; j < i; j++) {
                    RoutePoint p = path.get(j);
                    int segment = p.km() - path.get(j - 1).km();
                    int run = length > 0 ? Math.round((float) total * segment / length) : 0;
                    used += run;
                    res.set(j, new RoutePoint(p.station(), p.km(), false, run, 0));
                }
                RoutePoint stop = path.get(i);
                res.set(i, new RoutePoint(stop.station(), stop.km(), true, Math.max(0, total - used),
                        stop.stopDuration()));
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
        RouteNetwork.Path way = graph.paths(from).get(to);
        if (way == null) {
            throw ApiException.badRequest("Станции маршрута не связаны маршрутной сетью");
        }
        return way.km();
    }

    private static int defaultRunDuration(int km) {
        return Math.round(km * 60f / DEFAULT_SPEED_KMH);
    }

    /**
     * Сообщение из названий остановок через тире (station.name).
     */
    private String stopNames(List<RoutePoint> points) throws Exception {
        List<Long> stops = points.stream().filter(RoutePoint::haveStop).map(RoutePoint::station).toList();
        Map<Long, String> names = new HashMap<>();
        for (DbRec s : db.loadList("station", Map.of("id", new HashSet<>(stops)))) {
            names.put(s.getLong("id"), s.getString("name"));
        }
        return String.join(" — ", stops.stream().map(names::get).toList());
    }

    /**
     * Атрибуты и остановки маршрута не изменились - изменилась разве что дата окончания.
     */
    private static boolean sameRoute(Route r, RouteSave body) {
        return Objects.equals(trim(body.trainNumber()), r.trainNumber())
                && Objects.equals(trim(body.name()), r.name())
                && Objects.equals(body.routeKind(), r.routeKind())
                && Objects.equals(body.dbeg(), r.dbeg())
                && Objects.equals(body.duration(), r.duration())
                && Objects.equals(body.conductorHours(), r.conductorHours())
                && Objects.equals(body.lnpHours(), r.lnpHours())
                && Objects.equals(body.pemHours(), r.pemHours())
                && stopsOf(body.points()).equals(stopsOf(r.points()));
    }

    /**
     * Остановки по порядку: станция, время в пути и стоянка - для сравнения маршрутов.
     */
    private static List<List<Long>> stopsOf(List<RoutePoint> points) {
        return points.stream().filter(RoutePoint::haveStop)
                .map(p -> List.of(p.station(), (long) p.stopDuration())).toList();
    }

    /**
     * Создавать и править маршруты может администратор или планировщик (Ф-МАР-01).
     *
     * @throws ApiException 403 - не может
     */
    private void checkEditor() {
        CurrentUser user = currentUserService.get();
        if (!user.hasRole(RoleType.ADMIN) && !user.hasRole(RoleType.PLANNER)) {
            throw ApiException.forbidden("Создавать и править маршруты может администратор или планировщик");
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

    static Integer integer(DbRec r, String field) {
        return r.get(field) instanceof Number n ? n.intValue() : null;
    }

}
