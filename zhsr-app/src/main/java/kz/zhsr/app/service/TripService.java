package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.ActStatus;
import kz.zhsr.app.constants.ConductorCategory;
import kz.zhsr.app.constants.CouplingType;
import kz.zhsr.app.constants.CrewDuty;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.constants.WagonStatus;
import kz.zhsr.app.dto.ConductorCandidate;
import kz.zhsr.app.dto.ListSort;
import kz.zhsr.app.dto.StaffCandidate;
import kz.zhsr.app.dto.TripCrew;
import kz.zhsr.app.dto.TripFilter;
import kz.zhsr.app.dto.TripRow;
import kz.zhsr.app.dto.WagonCandidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Рейс: создание (Ф-РЕЙ-01), правка (Ф-РЕЙ-07), отмена (Ф-РЕЙ-05), формирование (Ф-РЕЙ-02, Ф-РЕЙ-06), отправление
 * (Ф-РЕЙ-03) и прибытие (Ф-РЕЙ-04).
 * <p>
 * Права: состав и бригаду рейса в статусе «план» меняют планировщик и администратор. Проводников по вагонам
 * распределяют они же в статусе «план», а начальник поезда этого рейса и администратор - и в статусе «выполняется».
 * Отправление и прибытие отмечает начальник поезда этого рейса. Администратор может всё. Смотреть формирование может тот, кому рейс
 * виден в списке рейсов.
 * <p>
 * Отправление и прибытие закрывают текущие записи журналов вагона и открывают новые в одной транзакции с кэшем
 * wagon_info; ссылки на открытые записи - в trip_wagon.
 */
@Service
public class TripService {

    // типы вагонов (wagon_type.code), для которых норма проводников не требуется
    private static final Set<String> NOT_PASSENGER = Set.of("RESTAURANT", "STAFF");

    static final List<Long> ACTIVE = List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS);

    /**
     * Атрибуты рейса.
     *
     * @param route     маршрут (route)
     * @param plannedDt плановое отправление, ISO с часовым поясом
     */
    public record TripBody(Long route, OffsetDateTime plannedDt) {
    }

    /**
     * Назначение на должность рейса.
     *
     * @param usr usr.id; null - снять с должности
     */
    public record DutyBody(Long usr) {
    }

    /**
     * Отметка отправления или прибытия.
     *
     * @param time фактическое время, ISO с часовым поясом; null - сейчас
     */
    public record MarkBody(OffsetDateTime time) {
    }

    private final Db db;
    private final CurrentUserService currentUserService;
    private final Visibility visibility;
    private final RouteService routeService;
    private final ListService listService;
    private final WagonJournal wagonJournal;

    public TripService(Db db, CurrentUserService currentUserService, Visibility visibility, RouteService routeService,
                       ListService listService, WagonJournal wagonJournal) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.visibility = visibility;
        this.routeService = routeService;
        this.listService = listService;
        this.wagonJournal = wagonJournal;
    }

    /**
     * Создать рейс в статусе «план» (Ф-РЕЙ-01). Создают планировщик и администратор.
     */
    @Transactional
    public TripRow create(TripBody body) throws Exception {
        requirePlanner();
        Map<String, Object> values = tripValues(body);
        values.put("trip_status_type", TripStatus.PLANNED);
        long id;
        try {
            id = db.insertRec("trip", values);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        return row(id);
    }

    /**
     * Исправить маршрут и плановое отправление рейса в статусе «план» (Ф-РЕЙ-07). Исправляют планировщик и администратор.
     */
    @Transactional
    public TripRow update(long id, TripBody body) throws Exception {
        DbRec trip = requireTrip(id);
        requirePlanner();
        if (trip.getLong("trip_status_type") != TripStatus.PLANNED) {
            throw ApiException.conflict("Рейс исправляется только в статусе «План»");
        }
        Map<String, Object> values = tripValues(body);
        values.put("id", id);
        try {
            db.updateRec("trip", values);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        evictCrew(load(id));
        return row(id);
    }

    /**
     * Отменить рейс в статусе «план» (Ф-РЕЙ-05): расположение вагонов не меняется. Отменяют планировщик и администратор.
     */
    @Transactional
    public TripRow cancel(long id) throws Exception {
        DbRec trip = requireTrip(id);
        if (!canEdit(currentUserService.get())) {
            throw ApiException.forbidden("Рейс отменяет планировщик");
        }
        if (trip.getLong("trip_status_type") != TripStatus.PLANNED) {
            throw ApiException.conflict("Отменяется только рейс в статусе «План»");
        }
        db.updateRec("trip", Map.of("id", id, "trip_status_type", TripStatus.CANCELLED));
        evictCrew(load(id));
        return row(id);
    }

    /**
     * Рейс, состав и бригада (GET /trips/{id}/crew).
     */
    public TripCrew crew(long id) throws Exception {
        requireVisible(id);
        return load(id);
    }

    /**
     * Проводники для набора в бригаду: станции отправления и уже назначенные. Занятые в пересекающемся по времени
     * рейсе не показываются; у того, кто до отправления ещё в другом рейсе, - этот рейс и когда освободится.
     */
    public List<ConductorCandidate> conductorCandidates(long id) throws Exception {
        requireVisible(id);
        TripCrew crew = load(id);
        DbRec trip = tripRec(id);
        Map<String, Object> params = new HashMap<>();
        params.put("trip", id);
        params.put("station", crew.trip().departureStation());
        params.put("duty", CrewDuty.CONDUCTOR);
        params.put("role", RoleType.CONDUCTOR);
        params.put("senior", ConductorCategory.SENIOR);
        List<DbRec> recs = db.loadSql(TripSql.conductorCandidates(), params);
        if (recs.isEmpty()) {
            return List.of();
        }
        Map<Long, List<DbRec>> others = otherTrips(recs.stream().map(r -> r.getLong("id")).toList(), id);
        OffsetDateTime start = trip.getDateTimeTZ("planned_dt");
        OffsetDateTime end = trip.getDateTimeTZ("arrival");

        List<ConductorCandidate> res = new ArrayList<>();
        for (DbRec r : recs) {
            long usr = r.getLong("id");
            boolean assigned = crew.crew().stream().anyMatch(m -> m.usr() == usr);
            List<DbRec> busy = others.getOrDefault(usr, List.of());
            if (!assigned && busy.stream().anyMatch(o -> overlaps(o, start, end))) {
                continue;
            }
            // рейс, который закончится последним из тех, что идут до отправления
            DbRec before = null;
            for (DbRec o : busy) {
                OffsetDateTime until = o.getDateTimeTZ("arrival");
                if (!until.isAfter(start) && (before == null || until.isAfter(before.getDateTimeTZ("arrival")))) {
                    before = o;
                }
            }
            res.add(new ConductorCandidate(usr, shortName(r), r.getBoolean("senior"), assigned,
                    before == null ? null : before.getString("train_number"),
                    before == null ? null : before.getString("name"),
                    before == null ? null : before.getDateTimeTZ("arrival")));
        }
        return res;
    }

    /**
     * Сотрудники для должности начальника поезда или ПЭМ: включённые, с такой ролью, не занятые в пересекающемся по
     * времени рейсе.
     */
    public List<StaffCandidate> staffCandidates(long id, long duty) throws Exception {
        requireVisible(id);
        List<DbRec> recs = db.loadSql(TripSql.staffCandidates(), Map.of("role", roleOf(duty)));
        if (recs.isEmpty()) {
            return List.of();
        }
        DbRec trip = tripRec(id);
        OffsetDateTime start = trip.getDateTimeTZ("planned_dt");
        OffsetDateTime end = trip.getDateTimeTZ("arrival");
        Map<Long, List<DbRec>> others = otherTrips(recs.stream().map(r -> r.getLong("id")).toList(), id);
        return recs.stream()
                .filter(r -> others.getOrDefault(r.getLong("id"), List.of()).stream()
                        .noneMatch(o -> overlaps(o, start, end)))
                .map(r -> new StaffCandidate(r.getLong("id"), shortName(r)))
                .toList();
    }

    /**
     * Вагоны для добавления в состав.
     */
    public List<WagonCandidate> wagonCandidates(long id) throws Exception {
        requireVisible(id);
        Long station = load(id).trip().departureStation();
        if (station == null) {
            return List.of();
        }
        return db.loadSql(TripSql.wagonCandidates(), Map.of("trip", id, "station", station, "states", ACTIVE)).stream()
                .map(r -> new WagonCandidate(r.getString("number"), r.getLong("wagon_type"),
                        r.getLong("location_type"), r.getLong("place"), r.getLong("wagon_status_type")))
                .toList();
    }

    /**
     * Проводники рейса: кто в списке - в бригаде, кого нет - уходит из бригады и с вагона. Новые проводники
     * становятся нераспределёнными.
     */
    @Transactional
    public TripCrew setConductors(long id, List<Long> users) throws Exception {
        DbRec trip = requirePlanEdit(id);
        Set<Long> keep = new HashSet<>(users);
        Set<Long> present = new HashSet<>();
        Set<Long> changed = new HashSet<>();
        try {
            for (DbRec r : db.loadList("trip_crew", Map.of("trip", id))) {
                long usr = r.getLong("usr");
                present.add(usr);
                if (r.getLong("crew_duty_type") == CrewDuty.CONDUCTOR && !keep.contains(usr)) {
                    db.deleteRec("trip_crew", r.getLong("id"));
                    changed.add(usr);
                }
            }
            for (long usr : keep) {
                if (present.contains(usr)) {
                    continue;
                }
                requireRole(usr, RoleType.CONDUCTOR);
                requireFree(trip, usr);
                db.insertRec("trip_crew", Map.of("trip", id, "usr", usr, "crew_duty_type", CrewDuty.CONDUCTOR));
                changed.add(usr);
            }
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        changed.forEach(currentUserService::evict);
        return load(id);
    }

    /**
     * Закрепить проводника за вагонами: один вагон или два соседних; пустой список - проводник снимается с вагона и
     * становится нераспределённым.
     */
    @Transactional
    public TripCrew setConductorWagons(long id, long user, List<String> numbers) throws Exception {
        DbRec trip = requireTrip(id);
        CurrentUser me = currentUserService.get();
        long state = trip.getLong("trip_status_type");
        boolean planner = canEdit(me);
        boolean admin = me.hasRole(RoleType.ADMIN);
        boolean allowed = (state == TripStatus.PLANNED && (planner || isChief(id, me)))
                || (state == TripStatus.IN_PROGRESS && (admin || isChief(id, me)));
        if (!allowed) {
            throw ApiException.forbidden(state == TripStatus.PLANNED || state == TripStatus.IN_PROGRESS
                    ? "Распределяет проводников планировщик или начальник поезда рейса"
                    : "Рейс закончен");
        }
        DbRec member = db.loadRec("trip_crew", Map.of("trip", id, "usr", user, "crew_duty_type", CrewDuty.CONDUCTOR));
        if (member == null) {
            throw ApiException.notFound("Проводника нет в бригаде рейса");
        }
        List<String> list = new ArrayList<>(new LinkedHashSet<>(numbers));
        if (list.size() > 2) {
            throw ApiException.badRequest("Проводник обслуживает один вагон или два соседних");
        }
        List<DbRec> composition = composition(id).stream().filter(r -> !detached(r)).toList();
        List<String> order = composition.stream().map(r -> r.getString("number")).toList();
        if (!order.containsAll(list)) {
            throw ApiException.badRequest("Вагона нет в составе рейса");
        }
        if (list.size() == 2 && Math.abs(order.indexOf(list.get(0)) - order.indexOf(list.get(1))) != 1) {
            throw ApiException.badRequest("Вторым вагоном можно выбрать только соседний");
        }
        Map<String, Object> values = new HashMap<>();
        values.put("id", member.getLong("id"));
        values.put("wagon", list.isEmpty() ? null : composition.get(order.indexOf(list.get(0))).getLong("wagon"));
        values.put("wagon2", list.size() < 2 ? null : composition.get(order.indexOf(list.get(1))).getLong("wagon"));
        db.updateRec("trip_crew", values);
        currentUserService.evict(user);
        return load(id);
    }

    /**
     * Назначить начальника поезда или ПЭМ; user пуст - снять с должности.
     */
    @Transactional
    public TripCrew setDuty(long id, long duty, DutyBody body) throws Exception {
        DbRec trip = requirePlanEdit(id);
        long role = roleOf(duty);
        Long user = body.usr();
        DbRec current = db.loadRec("trip_crew", Map.of("trip", id, "crew_duty_type", duty));
        if (current != null && user != null && current.getLong("usr") == user) {
            return load(id);
        }
        try {
            if (current != null) {
                db.deleteRec("trip_crew", current.getLong("id"));
                currentUserService.evict(current.getLong("usr"));
            }
            if (user != null) {
                if (db.loadRec("trip_crew", Map.of("trip", id, "usr", user)) != null) {
                    throw ApiException.conflict("Сотрудник уже в бригаде рейса");
                }
                requireRole(user, role);
                requireFree(trip, user);
                db.insertRec("trip_crew", Map.of("trip", id, "usr", user, "crew_duty_type", duty));
                currentUserService.evict(user);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        return load(id);
    }

    /**
     * Состав рейса целиком, по порядку: новые вагоны добавляются, вагоны не из списка уходят из состава и снимаются с
     * проводников. Расположение вагонов не меняется.
     */
    @Transactional
    public TripCrew setWagons(long id, List<String> numbers) throws Exception {
        requirePlanEdit(id);
        if (new HashSet<>(numbers).size() != numbers.size()) {
            throw ApiException.badRequest("Вагон указан в составе дважды");
        }
        // по одному: коллекцию параметра Db передаёт только как массив bigint, а номера вагонов - строки
        Map<String, Long> wagonIds = new HashMap<>();
        for (String number : numbers) {
            DbRec w = db.loadRec("wagon", Map.of("number", number));
            if (w == null) {
                throw ApiException.badRequest("Вагон " + number + " не найден");
            }
            wagonIds.put(number, w.getLong("id"));
        }
        Map<Long, DbRec> existing = new HashMap<>();
        for (DbRec r : db.loadList("trip_wagon", Map.of("trip", id))) {
            existing.put(r.getLong("wagon"), r);
        }
        List<Long> added = wagonIds.values().stream().filter(w -> !existing.containsKey(w)).toList();
        if (!added.isEmpty()) {
            List<String> busy = db.loadSql(TripSql.busyWagons(), Map.of("wagons", added, "trip", id, "states", ACTIVE))
                    .stream().map(r -> r.getString("number")).toList();
            if (!busy.isEmpty()) {
                throw ApiException.conflict("Вагон уже в составе другого активного рейса: " + String.join(", ", busy));
            }
        }

        Set<Long> kept = new HashSet<>(wagonIds.values());
        try {
            for (DbRec r : existing.values()) {
                if (!kept.contains(r.getLong("wagon"))) {
                    db.deleteRec("trip_wagon", r.getLong("id"));
                    releaseWagon(id, r.getLong("wagon"));
                }
            }
            int seq = 1;
            for (String number : numbers) {
                long wagon = wagonIds.get(number);
                DbRec row = existing.get(wagon);
                if (row == null) {
                    db.insertRec("trip_wagon", Map.of("trip", id, "wagon", wagon, "seq", seq));
                } else if (row.getInt("seq") != seq) {
                    db.updateRec("trip_wagon", Map.of("id", row.getLong("id"), "seq", seq));
                }
                seq++;
            }
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        return load(id);
    }

    /**
     * Отметить отправление (Ф-РЕЙ-03): рейс - «выполняется», вагоны состава - «в рейсе» и «в пути».
     */
    @Transactional
    public TripCrew depart(long id, MarkBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec trip = requireTrip(id);
        requireChief(id, me, "Отправление отмечает начальник поезда этого рейса");
        if (trip.getLong("trip_status_type") != TripStatus.PLANNED) {
            throw ApiException.conflict("Отправление отмечается только у рейса в статусе «План»");
        }
        TripCrew crew = load(id);
        requireReady(crew);

        OffsetDateTime time = body.time() == null ? OffsetDateTime.now() : body.time();
        db.updateRec("trip", Map.of("id", id, "trip_status_type", TripStatus.IN_PROGRESS, "departed_dt", time,
                "departed_by_usr", me.id()));
        for (DbRec row : composition(id)) {
            WagonJournal.Opened j = wagonJournal.move(row.getLong("wagon"), time, LocationType.TRIP, null, id,
                    WagonStatus.IN_TRIP);
            db.updateRec("trip_wagon", Map.of("id", row.getLong("id"), "depart_wagon_location", j.location(),
                    "depart_wagon_status", j.status()));
        }
        evictCrew(crew);
        return load(id);
    }

    /**
     * Отметить прибытие (Ф-РЕЙ-04): рейс - «выполнен», вагоны, которые едут в составе, - на станции прибытия и «в
     * простое». Вагоны, отцепленные в пути, не меняются.
     */
    @Transactional
    public TripCrew arrive(long id, MarkBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec trip = requireTrip(id);
        requireChief(id, me, "Прибытие отмечает начальник поезда этого рейса");
        if (trip.getLong("trip_status_type") != TripStatus.IN_PROGRESS) {
            throw ApiException.conflict("Прибытие отмечается только у рейса в статусе «Выполняется»");
        }
        OffsetDateTime time = body.time() == null ? OffsetDateTime.now() : body.time();
        OffsetDateTime departed = trip.getDateTimeTZ("departed_dt");
        if (departed != null && !time.isAfter(departed)) {
            throw ApiException.badRequest("Прибытие должно быть позже отправления");
        }
        TripCrew crew = load(id);
        Long station = crew.trip().arrivalStation();
        if (station == null) {
            throw ApiException.conflict("У маршрута рейса нет станции прибытия");
        }

        db.updateRec("trip", Map.of("id", id, "trip_status_type", TripStatus.DONE, "arrived_dt", time,
                "arrived_by_usr", me.id()));
        for (DbRec row : composition(id)) {
            if (row.getLong("location_type") != LocationType.TRIP || row.getLong("trip", null) == null
                    || row.getLong("trip") != id) {
                continue;
            }
            WagonJournal.Opened j = wagonJournal.move(row.getLong("wagon"), time, LocationType.STATION, station, null,
                    WagonStatus.IN_IDLE);
            db.updateRec("trip_wagon", Map.of("id", row.getLong("id"), "arrive_wagon_location", j.location(),
                    "arrive_wagon_status", j.status()));
        }
        evictCrew(crew);
        return load(id);
    }

    // --- внутреннее

    /**
     * Снять вагон с проводников рейса: второй вагон проводника становится первым.
     */
    void releaseWagon(long trip, long wagon) throws Exception {
        for (DbRec r : db.loadList("trip_crew", Map.of("trip", trip, "crew_duty_type", CrewDuty.CONDUCTOR))) {
            Long first = r.getLong("wagon", null);
            Long second = r.getLong("wagon2", null);
            if (!Long.valueOf(wagon).equals(first) && !Long.valueOf(wagon).equals(second)) {
                continue;
            }
            Map<String, Object> values = new HashMap<>();
            values.put("id", r.getLong("id"));
            values.put("wagon", Long.valueOf(wagon).equals(first) ? second : first);
            values.put("wagon2", null);
            db.updateRec("trip_crew", values);
            currentUserService.evict(r.getLong("usr"));
        }
    }

    /**
     * Готовность к отправлению: состав на станции отправления, начальник поезда и ПЭМ назначены, норма проводников
     * выполнена. Рабочий парк вагона (правило Ф-РЕЙ-03) не проверяется: в БД нет парка вагона.
     */
    private void requireReady(TripCrew crew) {
        if (crew.wagons().isEmpty()) {
            throw ApiException.conflict("Состав рейса не сформирован");
        }
        Long station = crew.trip().departureStation();
        List<String> away = crew.wagons().stream()
                .filter(w -> w.locationType() != LocationType.STATION || station == null || w.place() != station)
                .map(TripCrew.Wagon::number)
                .toList();
        if (!away.isEmpty()) {
            throw ApiException.conflict("Вагоны не на станции отправления: " + String.join(", ", away));
        }
        boolean chief = crew.crew().stream().anyMatch(m -> m.crewDutyType() == CrewDuty.LNP);
        boolean pem = crew.crew().stream().anyMatch(m -> m.crewDutyType() == CrewDuty.PEM);
        if (!chief || !pem) {
            throw ApiException.conflict("Не назначены начальник поезда и ПЭМ");
        }
        List<String> notOk = TripNorm.notOk(crew.wagons(), crew.crew());
        if (!notOk.isEmpty()) {
            throw ApiException.conflict("Не выполнена норма проводников: " + String.join(", ", notOk));
        }
    }

    TripCrew load(long id) throws Exception {
        DbRec trip = tripRec(id);
        List<Long> stops = RouteService.stops(
                routeService.stations(List.of(trip.getLong("route"))).getOrDefault(trip.getLong("route"), List.of()));

        Map<Long, String> numbers = new HashMap<>();
        List<TripCrew.Wagon> wagons = new ArrayList<>();
        for (DbRec r : composition(id)) {
            numbers.put(r.getLong("wagon"), r.getString("number"));
            wagons.add(new TripCrew.Wagon(r.getString("number"), r.getLong("wagon_type"),
                    !NOT_PASSENGER.contains(r.getString("code")), r.getLong("location_type"), r.getLong("place"),
                    r.getString("train_number"), r.getString("name"), detached(r)));
        }

        List<TripCrew.Member> crew = new ArrayList<>();
        for (DbRec r : db.loadSql(TripSql.crew(), Map.of("trip", id, "senior", ConductorCategory.SENIOR))) {
            List<String> memberWagons = new ArrayList<>();
            for (String field : List.of("wagon", "wagon2")) {
                Long wagon = r.getLong(field, null);
                if (wagon != null && numbers.containsKey(wagon)) {
                    memberWagons.add(numbers.get(wagon));
                }
            }
            crew.add(new TripCrew.Member(r.getLong("usr"), shortName(r), r.getLong("crew_duty_type"),
                    r.getBoolean("senior"), memberWagons));
        }

        return new TripCrew(new TripCrew.Trip(id, trip.getLong("route"), trip.getString("train_number"), trip.getString("name"),
                trip.getLong("trip_status_type"), trip.getLong("transport_type"), trip.getInt("duration"),
                trip.getDateTimeTZ("planned_dt"), trip.getDateTimeTZ("departed_dt"), trip.getDateTimeTZ("departure"),
                trip.getDateTimeTZ("arrival"), stops.isEmpty() ? null : stops.get(0),
                stops.isEmpty() ? null : stops.get(stops.size() - 1), stops), wagons, crew, acts(id));
    }

    private List<TripCrew.Act> acts(long id) throws Exception {
        Map<Long, List<String>> wagons = new HashMap<>();
        for (DbRec r : db.loadSql(TripSql.actWagons(), Map.of("trip", id))) {
            wagons.computeIfAbsent(r.getLong("coupling_act"), k -> new ArrayList<>()).add(r.getString("number"));
        }
        return db.loadSql(TripSql.acts(), Map.of("trip", id)).stream()
                .map(r -> new TripCrew.Act(r.getString("number"), r.getLong("coupling_type"), r.getLong("station"),
                        wagons.get(r.getLong("id")), r.getDateTimeTZ("act_dt"), r.getLong("act_status_type")))
                .toList();
    }

    private static boolean detached(DbRec row) {
        Long last = row.getLong("last_coupling", null);
        return last != null && last == CouplingType.DETACH;
    }

    private Map<String, Object> tripValues(TripBody body) throws Exception {
        if (body.route() == null || body.plannedDt() == null) {
            throw ApiException.badRequest("Укажите маршрут и плановое отправление");
        }
        DbRec route = db.loadRec("route", body.route());
        LocalDate end = route == null ? null : RouteService.localDate(route, "dend");
        if (route == null || (end != null && end.isBefore(LocalDate.now()))) {
            throw ApiException.badRequest("Маршрут не действует");
        }
        Map<String, Object> values = new HashMap<>();
        values.put("route", body.route());
        values.put("planned_dt", body.plannedDt());
        return values;
    }

    private TripRow row(long id) throws Exception {
        return listService.trips(new TripFilter(id, null, null, null, null, null, null), new ListSort(null, false)).get(0);
    }

    private void requirePlanner() {
        if (!canEdit(currentUserService.get())) {
            throw ApiException.forbidden("Рейс создаёт и исправляет планировщик");
        }
    }

    List<DbRec> composition(long id) throws Exception {
        return db.loadSql(TripSql.composition(), Map.of("trip", id, "done", ActStatus.DONE));
    }

    private DbRec tripRec(long id) throws Exception {
        DbRec trip = db.loadSqlRec(TripSql.trip(), Map.of("id", id));
        if (trip == null) {
            throw ApiException.notFound("Рейс не найден");
        }
        return trip;
    }

    DbRec requireTrip(long id) throws Exception {
        requireVisible(id);
        return tripRec(id);
    }

    /**
     * Рейс виден сотруднику так же, как в списке рейсов; иначе - как будто его нет.
     */
    private void requireVisible(long id) throws Exception {
        Visibility.Access access = visibility.access(currentUserService.get(), null);
        SqlQuery q = ListSql.trips(new TripFilter(id, null, null, null, null, null, null), access,
                new ListSort(null, false));
        if (db.loadSql(q.sql(), q.params()).isEmpty()) {
            throw ApiException.notFound("Рейс не найден");
        }
    }

    private DbRec requirePlanEdit(long id) throws Exception {
        DbRec trip = requireTrip(id);
        if (!canEdit(currentUserService.get())) {
            throw ApiException.forbidden("Состав и бригаду рейса меняет планировщик");
        }
        if (trip.getLong("trip_status_type") != TripStatus.PLANNED) {
            throw ApiException.conflict("Состав и бригада меняются только у рейса в статусе «План»");
        }
        return trip;
    }

    private static boolean canEdit(CurrentUser user) {
        return user.hasRole(RoleType.PLANNER) || user.hasRole(RoleType.ADMIN);
    }

    boolean isChief(long trip, CurrentUser user) throws Exception {
        return db.loadRec("trip_crew", Map.of("trip", trip, "usr", user.id(), "crew_duty_type", CrewDuty.LNP)) != null;
    }

    private void requireChief(long trip, CurrentUser user, String message) throws Exception {
        if (!user.hasRole(RoleType.ADMIN) && !isChief(trip, user)) {
            throw ApiException.forbidden(message);
        }
    }

    private void requireRole(long usr, long role) throws Exception {
        DbRec rec = db.loadRec("usr", usr);
        if (rec == null || rec.getBoolean("disabled")
                || db.loadRec("usr_role", Map.of("usr", usr, "role_type", role)) == null) {
            throw ApiException.badRequest("У сотрудника нет нужной роли");
        }
    }

    /**
     * Сотрудник не входит в бригаду другого активного рейса, пересекающегося с этим по времени.
     */
    private void requireFree(DbRec trip, long usr) throws Exception {
        OffsetDateTime start = trip.getDateTimeTZ("planned_dt");
        OffsetDateTime end = trip.getDateTimeTZ("arrival");
        for (DbRec other : otherTrips(List.of(usr), trip.getLong("id")).getOrDefault(usr, List.of())) {
            if (overlaps(other, start, end)) {
                throw ApiException.conflict("Сотрудник занят в рейсе " + other.getString("train_number") + " "
                        + other.getString("name"));
            }
        }
    }

    private Map<Long, List<DbRec>> otherTrips(List<Long> usrs, long trip) throws Exception {
        Map<Long, List<DbRec>> res = new HashMap<>();
        for (DbRec r : db.loadSql(TripSql.otherTrips(), Map.of("usrs", usrs, "trip", trip, "states", ACTIVE))) {
            res.computeIfAbsent(r.getLong("usr"), k -> new ArrayList<>()).add(r);
        }
        return res;
    }

    private static boolean overlaps(DbRec other, OffsetDateTime start, OffsetDateTime end) {
        return other.getDateTimeTZ("planned_dt").isBefore(end) && other.getDateTimeTZ("arrival").isAfter(start);
    }

    private static long roleOf(long duty) {
        if (duty == CrewDuty.LNP) {
            return RoleType.LNP;
        }
        if (duty == CrewDuty.PEM) {
            return RoleType.PEM;
        }
        throw ApiException.badRequest("Назначается начальник поезда или ПЭМ");
    }

    private static String shortName(DbRec usr) {
        return CurrentUser.shortName(usr.getString("full_name"));
    }

    private void evictCrew(TripCrew crew) {
        crew.crew().forEach(m -> currentUserService.evict(m.usr()));
    }

}
