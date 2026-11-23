package kz.zhsr.app.service.analytics;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.CrewDuty;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.TrainWagonKind;
import kz.zhsr.app.dto.analytics.GeoChart;
import kz.zhsr.app.dto.analytics.GeoPlaces;
import kz.zhsr.app.dto.analytics.GeoRoute;
import kz.zhsr.app.dto.analytics.GeoTrip;
import kz.zhsr.app.dto.analytics.GeoUsr;
import kz.zhsr.app.dto.analytics.GeoWagon;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.ref.WagonRef;
import kz.zhsr.app.service.ApiException;
import kz.zhsr.app.service.StageService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Диаграмма «Текущие рейсы»: ход рейсов и положения вагонов и проводников.
 */
@Service
public class GeoService {

    /** Роли, которым видны только свои рейсы: сотрудник без других ролей. */
    private static final Set<Long> OWN_TRIP_ROLES = Set.of(RoleType.CONDUCTOR, RoleType.PEM, RoleType.LNP);

    /** Наибольшая скорость поезда, км в минуту: дальше по линии следующая точка хода не ищется. */
    private static final double MAX_KM_PER_MINUTE = 3;

    /** Запас поиска следующей точки хода, км. */
    private static final double MATCH_RESERVE_KM = 5;

    private final Db db;
    private final CurrentUserService currentUserService;
    private final StageService stageService;
    private final RefResolver refResolver;

    public GeoService(Db db, CurrentUserService currentUserService, StageService stageService,
                      RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.stageService = stageService;
        this.refResolver = refResolver;
    }

    /**
     * Рейсы (GET /analytics/geo) за период [from, to). Проводник, ПЭМ и ЛНП (без других ролей)
     * видят только рейсы, в бригаде которых они состоят, а вагоны и проводников этих рейсов - до прибытия рейса.
     * Вагоны и проводники - только вне рейсов: у вагона и проводника в рейсе остаётся последнее положение в нём.
     *
     * @param withPoints с положениями вагонов и проводников; false - только маршруты и ход рейсов
     * @throws ApiException 400 - конец периода не позже начала
     */
    public GeoChart chart(OffsetDateTime from, OffsetDateTime to, boolean withPoints) throws Exception {
        if (!to.isAfter(from)) {
            throw ApiException.badRequest("Конец периода должен быть позже начала");
        }
        CurrentUser user = currentUserService.get();
        boolean own = user.roles().stream().allMatch(role -> OWN_TRIP_ROLES.contains(role.id()));

        List<GeoTrip> trips = loadTrips(from, to, own ? user.id() : null);
        List<Long> tripIds = trips.stream().map(GeoTrip::getId).toList();
        List<DbRec> tripPoints = tripIds.isEmpty() ? List.of() : db.loadSql(GeoSql.tripWagonPoints(),
                Map.of("trips", tripIds, "fact", TrainWagonKind.FACT, "to", to));
        Map<Long, List<DbRec>> pointsByTrip = UtDb.collectGroupBy_records(tripPoints, "trip");

        List<GeoRoute> routes = loadRoutes(from, to, trips);
        Map<Long, RouteLine> lines = addLines(routes);
        for (GeoTrip trip : trips) {
            trip.setTrack(track(trip, lines.get(trip.getRoute()), pointsByTrip.getOrDefault(trip.getId(), List.of()),
                    from));
        }

        List<GeoWagon> wagons = List.of();
        List<GeoUsr> usrs = List.of();
        if (withPoints) {
            wagons = loadWagons(trips, pointsByTrip, own, from, to);
            usrs = loadUsrs(trips, own, from, to);
        }
        refResolver.resolve(trips);
        refResolver.resolve(wagons);
        refResolver.resolve(usrs);
        return new GeoChart(from, routes, trips, wagons, usrs);
    }

    private List<GeoTrip> loadTrips(OffsetDateTime from, OffsetDateTime to, Long usr) throws Exception {
        Map<String, Object> params = new HashMap<>(Map.of("from", from, "to", to));
        if (usr != null) {
            params.put("usr", usr);
        }
        return db.loadSql(GeoSql.trips(usr != null), params, GeoTrip.class);
    }

    private List<GeoRoute> loadRoutes(OffsetDateTime from, OffsetDateTime to, List<GeoTrip> trips) throws Exception {
        return db.loadSql(GeoSql.routes(), Map.of("from", from.toLocalDate(), "to", to.toLocalDate(),
                "routes", trips.stream().map(GeoTrip::getRoute).distinct().toList()), GeoRoute.class);
    }

    /** Линии маршрутов по id маршрута; точки линий записываются в маршруты. У маршрута без станций линии нет. */
    private Map<Long, RouteLine> addLines(List<GeoRoute> routes) throws Exception {
        Set<Long> ids = new HashSet<>();
        routes.forEach(route -> ids.add(route.getId()));
        Map<Long, List<DbRec>> stations = ids.isEmpty() ? Map.of() : UtDb.collectGroupBy_records(
                db.loadList("route_station", Map.of("route", ids)), "route");
        Map<Long, RouteLine> res = new HashMap<>();
        for (GeoRoute route : routes) {
            List<DbRec> routeStations = stations.getOrDefault(route.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(station -> station.getInt("seq"))).toList();
            if (routeStations.isEmpty()) {
                continue;
            }
            RouteLine line = new RouteLine(stageService.lines(
                    routeStations.stream().map(station -> station.getLong("station")).toList()), routeStations);
            route.setPoints(line.points().stream()
                    .map(p -> new double[]{p[0], p[1], round(p[2], 100)}).toList());
            res.put(route.getId(), line);
        }
        return res;
    }

    /**
     * Ход поезда рейса - по вагону, у которого больше всего точек: он ехал дольше всех.
     *
     * @param points положения вагонов рейса по вагону и времени
     */
    private static List<double[]> track(GeoTrip trip, RouteLine line, List<DbRec> points, OffsetDateTime from) {
        if (line == null || points.isEmpty()) {
            return List.of();
        }
        List<DbRec> longest = UtDb.collectGroupBy_records(points, "wagon").values().stream()
                .max(Comparator.comparingInt(List::size)).orElseThrow();
        List<double[]> res = new ArrayList<>();
        double km = 0;
        OffsetDateTime previous = trip.getDepartedDt();
        for (DbRec point : longest) {
            OffsetDateTime dt = point.getDateTimeTZ("dt");
            double reach = minutes(previous, dt) * MAX_KM_PER_MINUTE + MATCH_RESERVE_KM;
            km = line.match(coordinate(point, "x"), coordinate(point, "y"), km, Math.min(line.length(), km + reach));
            res.add(new double[]{round(minutes(from, dt), 10), round(km, 100),
                    round(line.delay(km, minutes(trip.getPlannedDt(), dt)), 10)});
            previous = dt;
        }
        return res;
    }

    /** Вагоны вне рейсов; own - только вагоны рейсов trips, до прибытия рейса. */
    private List<GeoWagon> loadWagons(List<GeoTrip> trips, Map<Long, List<DbRec>> tripPoints, boolean own,
                                      OffsetDateTime from, OffsetDateTime to) throws Exception {
        Map<Long, List<DbRec>> busy = UtDb.collectGroupBy_records(db.loadSql(GeoSql.wagonTrips(),
                Map.of("from", from, "to", to, "fact", TrainWagonKind.FACT)), "wagon");
        if (!own) {
            List<DbRec> points = new ArrayList<>(db.loadSql(GeoSql.lastWagonPoints(), Map.of("from", from)));
            points.addAll(db.loadSql(GeoSql.wagonPoints(), Map.of("from", from, "to", to)));
            return toWagons(points, busy, from, null);
        }
        List<GeoWagon> res = new ArrayList<>();
        for (GeoTrip trip : trips) {
            res.addAll(toWagons(tripPoints.getOrDefault(trip.getId(), List.of()), busy, from, until(trip, from)));
        }
        return res;
    }

    private static List<GeoWagon> toWagons(List<DbRec> points, Map<Long, List<DbRec>> busy, OffsetDateTime from,
                                           Double until) {
        Map<Long, List<DbRec>> byWagon = UtDb.collectGroupBy_records(points, "wagon");
        List<GeoWagon> res = new ArrayList<>();
        byWagon.forEach((id, recs) -> {
            GeoWagon wagon = new GeoWagon();
            wagon.setWagon(new WagonRef(id));
            fill(wagon, recs, busy.getOrDefault(id, List.of()), from, until);
            res.add(wagon);
        });
        return res;
    }

    /** Проводники вне рейсов; own - только проводники рейсов trips, до прибытия рейса. */
    private List<GeoUsr> loadUsrs(List<GeoTrip> trips, boolean own, OffsetDateTime from, OffsetDateTime to)
            throws Exception {
        Map<Long, List<DbRec>> busy = UtDb.collectGroupBy_records(db.loadSql(GeoSql.usrTrips(),
                Map.of("from", from, "to", to, "conductor", CrewDuty.CONDUCTOR)), "usr");
        if (!own) {
            List<DbRec> points = new ArrayList<>(db.loadSql(GeoSql.lastUsrPoints(),
                    Map.of("from", from, "conductor", CrewDuty.CONDUCTOR)));
            points.addAll(db.loadSql(GeoSql.usrPoints(),
                    Map.of("from", from, "to", to, "conductor", CrewDuty.CONDUCTOR)));
            return toUsrs(points, busy, from, null);
        }
        if (trips.isEmpty()) {
            return List.of();
        }
        Map<Long, List<DbRec>> byTrip = UtDb.collectGroupBy_records(db.loadSql(GeoSql.tripUsrPoints(),
                Map.of("trips", trips.stream().map(GeoTrip::getId).toList(), "conductor", CrewDuty.CONDUCTOR,
                        "from", from, "to", to)), "trip");
        List<GeoUsr> res = new ArrayList<>();
        for (GeoTrip trip : trips) {
            res.addAll(toUsrs(byTrip.getOrDefault(trip.getId(), List.of()), busy, from, until(trip, from)));
        }
        return res;
    }

    private static List<GeoUsr> toUsrs(List<DbRec> points, Map<Long, List<DbRec>> busy, OffsetDateTime from,
                                       Double until) {
        Map<Long, List<DbRec>> byUsr = UtDb.collectGroupBy_records(points, "usr");
        List<GeoUsr> res = new ArrayList<>();
        byUsr.forEach((id, recs) -> {
            GeoUsr usr = new GeoUsr();
            usr.setUsr(new UserRef(id));
            fill(usr, recs, busy.getOrDefault(id, List.of()), from, until);
            res.add(usr);
        });
        return res;
    }

    /** Минута, до которой видно вагоны и проводников рейса; null - рейс в пути. */
    private static Double until(GeoTrip trip, OffsetDateTime from) {
        return trip.getArrivedDt() == null ? null : minutes(from, trip.getArrivedDt());
    }

    /**
     * Заполнить положения: точки вне рейсов и последняя точка каждого рейса - там вагон или проводник остаётся после
     * него.
     *
     * @param points точки по времени
     * @param busy   рейсы: dbeg, dend (null - рейс идёт)
     */
    private static void fill(GeoPlaces places, List<DbRec> points, List<DbRec> busy, OffsetDateTime from,
                             Double until) {
        places.setUntil(until);
        for (DbRec trip : busy) {
            OffsetDateTime end = trip.getDateTimeTZ("dend");
            places.getTrips().add(new Double[]{round(minutes(from, trip.getDateTimeTZ("dbeg")), 10),
                    end == null ? null : round(minutes(from, end), 10)});
        }
        for (int i = 0; i < points.size(); i++) {
            DbRec trip = tripAt(busy, points.get(i));
            boolean sameTripNext = trip != null && i + 1 < points.size() && tripAt(busy, points.get(i + 1)) == trip;
            if (!sameTripNext) {
                places.getPoints().add(toPoint(points.get(i), from));
            }
        }
    }

    /** Рейс, в котором сделана точка; null - вне рейсов. */
    private static DbRec tripAt(List<DbRec> busy, DbRec point) {
        OffsetDateTime dt = point.getDateTimeTZ("dt");
        for (DbRec trip : busy) {
            OffsetDateTime end = trip.getDateTimeTZ("dend");
            if (!dt.isBefore(trip.getDateTimeTZ("dbeg")) && (end == null || !dt.isAfter(end))) {
                return trip;
            }
        }
        return null;
    }

    private static double[] toPoint(DbRec rec, OffsetDateTime from) {
        return new double[]{round(minutes(from, rec.getDateTimeTZ("dt")), 10),
                round(coordinate(rec, "x"), 10000), round(coordinate(rec, "y"), 10000)};
    }

    private static double coordinate(DbRec rec, String field) {
        return ((Number) rec.get(field)).doubleValue();
    }

    private static double minutes(OffsetDateTime from, OffsetDateTime to) {
        return Duration.between(from, to).toSeconds() / 60.0;
    }

    private static double round(double value, double scale) {
        return Math.round(value * scale) / scale;
    }
}
