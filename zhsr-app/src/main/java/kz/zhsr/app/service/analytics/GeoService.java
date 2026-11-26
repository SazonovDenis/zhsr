package kz.zhsr.app.service.analytics;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtCnv;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.CrewDuty;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.TrainWagonKind;
import kz.zhsr.app.constants.TrainWagonPlanStatus;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.constants.WagonResponsibleKind;
import kz.zhsr.app.constants.WagonResponsiblePlanStatus;
import kz.zhsr.app.constants.WagonResponsibleTripPoint;
import kz.zhsr.app.dto.analytics.GeoChange;
import kz.zhsr.app.dto.analytics.GeoChart;
import kz.zhsr.app.dto.analytics.GeoLocation;
import kz.zhsr.app.dto.analytics.GeoResponsible;
import kz.zhsr.app.dto.analytics.GeoRoute;
import kz.zhsr.app.dto.analytics.GeoState;
import kz.zhsr.app.dto.analytics.GeoTrip;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.ref.WagonRef;
import kz.zhsr.app.service.ApiException;
import kz.zhsr.app.service.StageService;
import kz.zhsr.app.service.TrainJournal;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Карты «Текущие рейсы» и поезда: ход рейсов по координатам поезда и данные для расчёта циклов рейсов - записи прицепок
 * и отцепок, расположение вагонов и приписка проводников. Где на карте поезд, вагоны и проводники на момент, считает
 * фронтенд.
 */
@Service
public class GeoService {

    /** Роли, которым видны только свои рейсы: сотрудник без других ролей. */
    private static final Set<Long> OWN_TRIP_ROLES = Set.of(RoleType.CONDUCTOR, RoleType.PEM, RoleType.LNP);

    /** Состояния рейсов цикла. */
    private static final List<Long> CYCLE_STATES = List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS,
            TripStatus.DONE);

    /** Наибольшая скорость поезда, км в минуту: дальше по линии следующая точка хода не ищется. */
    private static final double MAX_KM_PER_MINUTE = 3;

    /** Запас поиска следующей точки хода, км. */
    private static final double MATCH_RESERVE_KM = 5;

    private final Db db;
    private final CurrentUserService currentUserService;
    private final StageService stageService;
    private final TrainJournal trainJournal;
    private final RefResolver refResolver;

    public GeoService(Db db, CurrentUserService currentUserService, StageService stageService,
                      TrainJournal trainJournal, RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.stageService = stageService;
        this.trainJournal = trainJournal;
        this.refResolver = refResolver;
    }

    /**
     * «Текущие рейсы» (GET /analytics/geo) за период [from, to): рейсы, которые шли в период, и неотправленные с плановым
     * отправлением в периоде. Проводник, ПЭМ и ЛНП (без других ролей) видят только рейсы, в бригаде которых они состоят.
     *
     * @param details с данными циклов рейсов; false - только маршруты и ход рейсов
     * @throws ApiException 400 - конец периода не позже начала
     */
    public GeoChart chart(OffsetDateTime from, OffsetDateTime to, boolean details) throws Exception {
        if (!to.isAfter(from)) {
            throw ApiException.badRequest("Конец периода должен быть позже начала");
        }
        List<GeoTrip> trips = visible(db.loadSql(GeoSql.periodTrips(),
                Map.of("from", from, "to", to, "planned", TripStatus.PLANNED), GeoTrip.class));
        List<GeoRoute> routes = db.loadSql(GeoSql.routes(), Map.of("from", from.toLocalDate(), "to", to.toLocalDate(),
                "routes", trips.stream().map(GeoTrip::getRoute).distinct().toList()), GeoRoute.class);
        return build(from, to, routes, trips, details);
    }

    /**
     * Карта поезда (GET /trains/{id}/geo): все рейсы поезда в плане, в пути и выполненные с формирования поезда по
     * текущее время. Видимость рейсов - как у {@link #chart}.
     *
     * @throws ApiException 404 - нет поезда
     */
    public GeoChart trainChart(long train) throws Exception {
        DbRec rec = db.loadRec("train", train);
        if (rec == null) {
            throw ApiException.notFound("Поезд не найден");
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime dbeg = rec.getDateTimeTZ("dbeg");
        OffsetDateTime from = dbeg.isBefore(now) ? dbeg : now.minusMinutes(1);

        List<GeoTrip> trips = visible(db.loadSql(GeoSql.trainTrips(),
                Map.of("trains", List.of(train), "states", CYCLE_STATES), GeoTrip.class));
        List<GeoRoute> routes = trips.isEmpty() ? List.of() : db.loadSql(GeoSql.routesById(),
                Map.of("routes", trips.stream().map(GeoTrip::getRoute).distinct().toList()), GeoRoute.class);
        return build(from, now, routes, trips, true);
    }

    private List<GeoTrip> visible(List<GeoTrip> trips) throws Exception {
        CurrentUser user = currentUserService.get();
        if (!user.roles().stream().allMatch(role -> OWN_TRIP_ROLES.contains(role.id()))) {
            return trips;
        }
        Set<Long> own = new HashSet<>();
        db.loadList("trip_crew", Map.of("usr", user.id())).forEach(r -> own.add(r.getLong("trip")));
        return trips.stream().filter(trip -> own.contains(trip.getId())).toList();
    }

    private GeoChart build(OffsetDateTime from, OffsetDateTime to, List<GeoRoute> routes, List<GeoTrip> trips,
                           boolean details) throws Exception {
        setTracks(trips, routes, from, to);
        List<GeoState> states = List.of();
        List<GeoLocation> locations = List.of();
        List<GeoResponsible> responsibles = List.of();
        if (details && !trips.isEmpty()) {
            Set<Long> trains = new HashSet<>();
            trips.forEach(trip -> trains.add(trip.getTrain().getId()));
            fillCycles(trips, trains);
            states = db.loadList("train_wagon", Map.of("train", trains), GeoState.class);
            Set<Long> wagons = new HashSet<>();
            states.forEach(state -> wagons.add(state.getWagon()));
            trips.forEach(trip -> trip.getChanges().forEach(change -> wagons.add(change.getWagon().getId())));
            if (!wagons.isEmpty()) {
                locations = loadLocations(wagons, from);
                responsibles = loadResponsibles(trips, wagons, from);
            }
        }
        refResolver.resolve(trips);
        refResolver.resolve(locations);
        return new GeoChart(from, routes, trips, states, locations, responsibles, stageService.stations());
    }

    /** Записать в маршруты линии и остановки, в отправленные рейсы - ход поезда. */
    private void setTracks(List<GeoTrip> trips, List<GeoRoute> routes, OffsetDateTime from, OffsetDateTime to)
            throws Exception {
        Map<Long, RouteLine> lines = addLines(routes);
        List<Long> departed = trips.stream().filter(trip -> trip.getDepartedDt() != null).map(GeoTrip::getId).toList();
        Map<Long, List<DbRec>> points = departed.isEmpty() ? Map.of() : UtDb.collectGroupBy_records(
                db.loadSql(GeoSql.tripPoints(), Map.of("trips", departed, "to", to)), "trip");
        for (GeoTrip trip : trips) {
            trip.setTrack(track(trip, lines.get(trip.getRoute()), points.getOrDefault(trip.getId(), List.of()), from));
        }
    }

    /**
     * Записать в рейсы начала циклов, записи циклов и проводников. Цикл считается по всем рейсам поезда, и не вошедшим в
     * отбор.
     */
    private void fillCycles(List<GeoTrip> trips, Set<Long> trains) throws Exception {
        Map<Long, GeoTrip> shown = new HashMap<>();
        trips.forEach(trip -> shown.put(trip.getId(), trip));
        List<GeoTrip> all = db.loadSql(GeoSql.trainTrips(), Map.of("trains", trains, "states", CYCLE_STATES),
                GeoTrip.class);
        OffsetDateTime now = OffsetDateTime.now();
        for (long train : trains) {
            // по порядку отправления
            List<GeoTrip> ordered = all.stream().filter(trip -> trip.getTrain().getId() == train).toList();
            if (ordered.isEmpty()) {
                continue;
            }
            List<OffsetDateTime> ends = ordered.stream().map(trip -> expectedArrival(trip, now)).toList();
            for (int i = 1; i < ordered.size(); i++) {
                GeoTrip trip = shown.get(ordered.get(i).getId());
                if (trip != null) {
                    trip.setCycleStart(ends.get(i - 1));
                }
            }
            OffsetDateTime lastFact = trainJournal.lastFact(train);
            List<DbRec> journal = db.loadList("train_wagon_change", Map.of("train", train));
            Map<Long, OffsetDateTime> times = new HashMap<>();
            journal.forEach(r -> times.put(r.getLong("id"), r.getDateTimeTZ("dt")));
            for (DbRec rec : journal) {
                boolean fact = rec.getInt("kind") == TrainWagonKind.FACT;
                if (!fact && UtCnv.toLong(rec.get("plan_status")) != TrainWagonPlanStatus.WAITING) {
                    continue;
                }
                OffsetDateTime dt = rec.getDateTimeTZ("dt");
                OffsetDateTime moment = !fact && lastFact != null && dt.isBefore(lastFact) ? lastFact : dt;
                GeoTrip trip = shown.get(ordered.get(cycleOf(ordered, ends, rec.getLong("trip"), moment))
                        .getId());
                if (trip != null) {
                    GeoChange change = toChange(rec, moment);
                    Long plan = rec.getLong("plan");
                    change.setPlanDt(!fact ? dt : plan == null ? null : times.get(plan));
                    trip.getChanges().add(change);
                }
            }
        }
        for (GeoTrip trip : trips) {
            trip.getChanges().sort(Comparator.comparing(GeoChange::getMoment).thenComparingLong(GeoChange::getId));
        }
        for (DbRec rec : db.loadList("trip_crew",
                Map.of("trip", shown.keySet(), "crew_duty_type", CrewDuty.CONDUCTOR))) {
            List<UserRef> conductors = shown.get(rec.getLong("trip")).getConductors();
            long usr = rec.getLong("usr");
            if (conductors.stream().noneMatch(x -> x.getId() == usr)) {
                conductors.add(new UserRef(usr));
            }
        }
    }

    /**
     * Номер рейса, в цикл которого входит запись: рейс записи; у записи вне рейса - первый рейс, который прибывает не
     * раньше неё, а после прибытия всех - последний.
     */
    private static int cycleOf(List<GeoTrip> ordered, List<OffsetDateTime> ends, Long trip, OffsetDateTime moment) {
        for (int i = 0; trip != null && i < ordered.size(); i++) {
            if (ordered.get(i).getId() == trip) {
                return i;
            }
        }
        for (int i = 0; i < ordered.size(); i++) {
            if (!moment.isAfter(ends.get(i))) {
                return i;
            }
        }
        return ordered.size() - 1;
    }

    /**
     * Прибытие рейса: фактическое; у неприбывшего - плановое со сдвигом на опоздание отправления (у неотправленного - на
     * просрочку отправления), но не раньше now.
     */
    private static OffsetDateTime expectedArrival(GeoTrip trip, OffsetDateTime now) {
        if (trip.getArrivedDt() != null) {
            return trip.getArrivedDt();
        }
        OffsetDateTime departure = trip.getDepartedDt() != null ? trip.getDepartedDt()
                : now.isAfter(trip.getPlannedDt()) ? now : trip.getPlannedDt();
        OffsetDateTime res = trip.getArrival().plus(Duration.between(trip.getPlannedDt(), departure));
        return res.isAfter(now) ? res : now;
    }

    private static GeoChange toChange(DbRec rec, OffsetDateTime moment) {
        GeoChange res = new GeoChange();
        res.setId(rec.getLong("id"));
        res.setWagon(new WagonRef(rec.getLong("wagon")));
        res.setKind(rec.getInt("kind"));
        res.setChangeType(rec.getInt("change_type"));
        res.setDt(rec.getDateTimeTZ("dt"));
        res.setMoment(moment);
        res.setStation(rec.getLong("station"));
        return res;
    }

    private List<GeoLocation> loadLocations(Set<Long> wagons, OffsetDateTime from) throws Exception {
        Map<Long, Long> depots = stationsOf("depot");
        Map<Long, Long> yards = stationsOf("yard");
        List<GeoLocation> res = new ArrayList<>();
        for (DbRec rec : db.loadList("wagon_location", Map.of("wagon", wagons))) {
            if (endsBefore(rec, from)) {
                continue;
            }
            GeoLocation location = new GeoLocation();
            location.setWagon(rec.getLong("wagon"));
            location.setDbeg(rec.getDateTimeTZ("dbeg"));
            location.setDend(rec.getDateTimeTZ("dend"));
            Long depot = rec.getLong("depot");
            Long yard = rec.getLong("yard");
            Long trip = rec.getLong("trip");
            location.setDepot(depot);
            location.setYard(yard);
            location.setStation(depot != null ? depots.get(depot)
                    : yard != null ? yards.get(yard) : rec.getLong("station"));
            location.setTrip(trip == null ? null : new TripRef(trip));
            res.add(location);
        }
        return res;
    }

    /** Станции записей таблицы table (депо, парки отстоя) по id. */
    private Map<Long, Long> stationsOf(String table) throws Exception {
        Map<Long, Long> res = new HashMap<>();
        db.loadList(table, Map.of()).forEach(r -> res.put(r.getLong("id"), r.getLong("station")));
        return res;
    }

    private static boolean endsBefore(DbRec rec, OffsetDateTime from) {
        OffsetDateTime dend = rec.getDateTimeTZ("dend");
        return dend != null && !dend.isAfter(from);
    }

    private static String stageKey(DbRec rec) {
        return rec.getLong("wagon") + ":" + rec.getLong("trip") + ":" + rec.getLong("trip_point");
    }

    /** Плановое время стадии рейса факта ответственных; null - факт вне стадии или рейса нет среди trips. */
    private static OffsetDateTime stageTime(DbRec rec, Map<String, OffsetDateTime> done, List<GeoTrip> trips) {
        Long trip = rec.getLong("trip");
        Long point = rec.getLong("trip_point");
        if (trip == null || point == null) {
            return null;
        }
        OffsetDateTime plan = done.get(stageKey(rec));
        if (plan != null) {
            return plan;
        }
        return trips.stream().filter(x -> x.getId() == trip).findFirst()
                .map(x -> point == WagonResponsibleTripPoint.DEPARTURE ? x.getPlannedDt() : x.getArrival())
                .orElse(null);
    }

    /** Периоды ответственных вагонов: факты с проводниками рейсов и все ожидающие планы (план сменяет прежних). */
    private List<GeoResponsible> loadResponsibles(List<GeoTrip> trips, Set<Long> wagons, OffsetDateTime from)
            throws Exception {
        Set<Long> conductors = new HashSet<>();
        trips.forEach(trip -> trip.getConductors().forEach(x -> conductors.add(x.getId())));
        if (conductors.isEmpty()) {
            return List.of();
        }
        List<DbRec> all = db.loadList("wagon_responsible", Map.of("wagon", wagons));
        // отработанные планы по вагону, рейсу и стадии
        Map<String, OffsetDateTime> done = new HashMap<>();
        all.stream()
                .filter(rec -> rec.getInt("kind") == WagonResponsibleKind.PLAN
                        && UtCnv.toLong(rec.get("plan_status")) == WagonResponsiblePlanStatus.DONE)
                .forEach(rec -> done.put(stageKey(rec), rec.getDateTimeTZ("dbeg")));
        List<DbRec> recs = all.stream()
                .filter(rec -> rec.getInt("kind") == WagonResponsibleKind.FACT ? !endsBefore(rec, from)
                        : UtCnv.toLong(rec.get("plan_status")) == WagonResponsiblePlanStatus.WAITING)
                .toList();
        if (recs.isEmpty()) {
            return List.of();
        }
        Map<Long, List<DbRec>> usrs = UtDb.collectGroupBy_records(db.loadList("wagon_responsible_usr",
                Map.of("wagon_responsible", UtDb.uniqueValues(recs, "id"))), "wagon_responsible");
        List<GeoResponsible> res = new ArrayList<>();
        for (DbRec rec : recs) {
            GeoResponsible responsible = new GeoResponsible();
            responsible.setWagon(rec.getLong("wagon"));
            responsible.setKind(rec.getInt("kind"));
            responsible.setDbeg(rec.getDateTimeTZ("dbeg"));
            responsible.setDend(rec.getDateTimeTZ("dend"));
            responsible.setPlanDt(responsible.getKind() == WagonResponsibleKind.PLAN ? responsible.getDbeg()
                    : stageTime(rec, done, trips));
            for (DbRec usr : usrs.getOrDefault(rec.getLong("id"), List.of())) {
                if (conductors.contains(usr.getLong("usr"))) {
                    responsible.getUsrs().add(usr.getLong("usr"));
                }
            }
            if (responsible.getKind() == WagonResponsibleKind.PLAN || !responsible.getUsrs().isEmpty()) {
                res.add(responsible);
            }
        }
        return res;
    }

    /** Линии маршрутов по id маршрута; точки линий и остановки записываются в маршруты. У маршрута без станций линии нет. */
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
            route.setStops(line.stops());
            res.put(route.getId(), line);
        }
        return res;
    }

    /**
     * Ход поезда рейса.
     *
     * @param points положения поезда за время рейса по времени
     */
    private static List<double[]> track(GeoTrip trip, RouteLine line, List<DbRec> points, OffsetDateTime from) {
        if (line == null || points.isEmpty()) {
            return List.of();
        }
        List<double[]> res = new ArrayList<>();
        double km = 0;
        OffsetDateTime previous = trip.getDepartedDt();
        for (DbRec point : points) {
            OffsetDateTime dt = point.getDateTimeTZ("dt");
            double reach = minutes(previous, dt) * MAX_KM_PER_MINUTE + MATCH_RESERVE_KM;
            km = line.match(coordinate(point, "x"), coordinate(point, "y"), km, Math.min(line.length(), km + reach));
            res.add(new double[]{round(minutes(from, dt), 10), round(km, 100),
                    round(line.delay(km, minutes(trip.getPlannedDt(), dt)), 10)});
            previous = dt;
        }
        return res;
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
