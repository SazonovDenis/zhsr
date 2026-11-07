package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.kis.kisdbtools.repository.DbRecMapper;
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
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.trip.ConductorCandidate;
import kz.zhsr.app.dto.trip.StaffCandidate;
import kz.zhsr.app.dto.trip.TripCrew;
import kz.zhsr.app.dto.trip.TripFilter;
import kz.zhsr.app.dto.trip.TripRow;
import kz.zhsr.app.dto.trip.WagonCandidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** Время в сообщении о занятости сотрудника. */
    private static final DateTimeFormatter BUSY_UNTIL =
            DateTimeFormatter.ofPattern("dd.MM HH:mm");

    /** Рейсы, которые занимают сотрудника: ещё не прибывшие и прибывшие - до фактического прибытия. */
    private static final List<Long> BUSY = List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS, TripStatus.DONE);

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
    private final RefResolver refResolver;

    public TripService(Db db, CurrentUserService currentUserService, Visibility visibility, RouteService routeService,
                       ListService listService, WagonJournal wagonJournal, RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.visibility = visibility;
        this.routeService = routeService;
        this.listService = listService;
        this.wagonJournal = wagonJournal;
        this.refResolver = refResolver;
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
        params.put("station", crew.getTrip().getDepartureStation());
        params.put("duty", CrewDuty.CONDUCTOR);
        params.put("role", RoleType.CONDUCTOR);
        params.put("senior", ConductorCategory.SENIOR);
        List<ConductorCandidate> candidates = db.loadSql(TripSql.conductorCandidates(), params,
                ConductorCandidate.class);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, List<DbRec>> others = otherTrips(candidates.stream().map(ConductorCandidate::getId).toList(), id);
        OffsetDateTime start = trip.getDateTimeTZ("planned_dt");
        OffsetDateTime end = trip.getDateTimeTZ("arrival");

        List<ConductorCandidate> res = new ArrayList<>();
        for (ConductorCandidate c : candidates) {
            long usr = c.getId();
            boolean assigned = crew.getCrew().stream().anyMatch(m -> m.getUsr().getId() == usr);
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
            c.setAssigned(assigned);
            if (before != null) {
                c.setBusyTrip(new TripRef(before.getLong("trip")));
                c.setBusyUntil(before.getDateTimeTZ("arrival"));
            }
            res.add(c);
        }
        refResolver.resolve(res);
        // кандидат сам является ссылкой, поэтому resolve не заходит в него: рейсы заполняются отдельно
        refResolver.resolve(res.stream().map(ConductorCandidate::getBusyTrip).filter(Objects::nonNull).toList());
        return res;
    }

    /**
     * Сотрудники для должности начальника поезда или ПЭМ: включённые, с такой ролью. Занятые в пересекающемся по
     * времени рейсе тоже в списке - с этим рейсом и временем, до которого заняты; назначить их не даст requireFree.
     */
    public List<StaffCandidate> staffCandidates(long id, long duty) throws Exception {
        requireVisible(id);
        List<StaffCandidate> staff = db.loadSql(TripSql.staffCandidates(), Map.of("role", roleOf(duty)),
                StaffCandidate.class);
        if (staff.isEmpty()) {
            return List.of();
        }
        DbRec trip = tripRec(id);
        OffsetDateTime start = trip.getDateTimeTZ("planned_dt");
        OffsetDateTime end = trip.getDateTimeTZ("arrival");
        Map<Long, List<DbRec>> others = otherTrips(staff.stream().map(UserRef::getId).toList(), id);
        for (StaffCandidate c : staff) {
            others.getOrDefault(c.getId(), List.of()).stream()
                    .filter(o -> overlaps(o, start, end))
                    .max(Comparator.comparing(o -> o.getDateTimeTZ("arrival")))
                    .ifPresent(o -> {
                        c.setBusyTrip(new TripRef(o.getLong("trip")));
                        c.setBusyUntil(o.getDateTimeTZ("arrival"));
                    });
        }
        refResolver.resolve(staff);
        // кандидат сам является ссылкой, поэтому resolve не заходит в него: рейсы заполняются отдельно
        refResolver.resolve(staff.stream().map(StaffCandidate::getBusyTrip).filter(Objects::nonNull).toList());
        return staff;
    }

    /**
     * Вагоны для добавления в состав.
     */
    public List<WagonCandidate> wagonCandidates(long id) throws Exception {
        requireVisible(id);
        Long station = load(id).getTrip().getDepartureStation();
        if (station == null) {
            return List.of();
        }
        return db.loadSql(TripSql.wagonCandidates(), Map.of("trip", id, "station", station, "states", ACTIVE),
                WagonCandidate.class);
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
        Map<Long, DbRec> existing = UtDb.collectGroupBy_record(db.loadList("trip_wagon", Map.of("trip", id)), "wagon");
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
        if (time.isAfter(OffsetDateTime.now())) {
            throw ApiException.badRequest("Укажите время не позже текущего");
        }
        db.updateRec("trip", Map.of("id", id, "trip_status_type", TripStatus.IN_PROGRESS, "departed_dt", time,
                "departed_by_usr", me.id(), "departure_marked_dt", OffsetDateTime.now()));
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
        if (time.isAfter(OffsetDateTime.now())) {
            throw ApiException.badRequest("Укажите время не позже текущего");
        }
        OffsetDateTime departed = trip.getDateTimeTZ("departed_dt");
        if (departed != null && !time.isAfter(departed)) {
            throw ApiException.badRequest("Прибытие должно быть позже отправления");
        }
        TripCrew crew = load(id);
        Long station = crew.getTrip().getArrivalStation();
        if (station == null) {
            throw ApiException.conflict("У маршрута рейса нет станции прибытия");
        }

        db.updateRec("trip", Map.of("id", id, "trip_status_type", TripStatus.DONE, "arrived_dt", time,
                "arrived_by_usr", me.id(), "arrival_marked_dt", OffsetDateTime.now()));
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

    /** Сколько после отметки отправления или прибытия её можно исправить или отменить. */
    public static final Duration MARK_EDIT_PERIOD = Duration.ofMinutes(10);

    /** Отметка отправления или прибытия: колонки рейса и строки состава, статус рейса с отметкой и без неё. */
    private enum Mark {
        DEPART("departed_dt", "departed_by_usr", "departure_marked_dt", "depart", TripStatus.IN_PROGRESS,
                TripStatus.PLANNED, "отправления"),
        ARRIVE("arrived_dt", "arrived_by_usr", "arrival_marked_dt", "arrive", TripStatus.DONE,
                TripStatus.IN_PROGRESS, "прибытия");

        final String time;
        final String by;
        final String marked;
        final String wagonColumns;
        final long status;
        final long statusBefore;
        final String text;

        Mark(String time, String by, String marked, String wagonColumns, long status, long statusBefore,
             String text) {
            this.time = time;
            this.by = by;
            this.marked = marked;
            this.wagonColumns = wagonColumns;
            this.status = status;
            this.statusBefore = statusBefore;
            this.text = text;
        }
    }

    /**
     * Исправить время отправления (Ф-РЕЙ-03): в течение {@link #MARK_EDIT_PERIOD} после отметки, начальник поезда
     * рейса. Новое время - раньше первого изменения вагонов после отправления (отцепки, прицепки, передачи).
     */
    @Transactional
    public TripCrew retimeDeparture(long id, MarkBody body) throws Exception {
        return retime(id, body, Mark.DEPART);
    }

    /** Исправить время прибытия (Ф-РЕЙ-04): как {@link #retimeDeparture}; прибытие - позже отправления. */
    @Transactional
    public TripCrew retimeArrival(long id, MarkBody body) throws Exception {
        return retime(id, body, Mark.ARRIVE);
    }

    /**
     * Отменить отправление (Ф-РЕЙ-03): в течение {@link #MARK_EDIT_PERIOD} после отметки, начальник поезда рейса.
     * Рейс - снова «план», записи журналов вагонов, открытые отправлением, удаляются. Если после отправления у вагонов
     * были изменения (отцепка, прицепка, передача), отмены нет.
     */
    @Transactional
    public TripCrew cancelDeparture(long id) throws Exception {
        return cancel(id, Mark.DEPART);
    }

    /** Отменить прибытие (Ф-РЕЙ-04): как {@link #cancelDeparture}; рейс - снова «выполняется». */
    @Transactional
    public TripCrew cancelArrival(long id) throws Exception {
        return cancel(id, Mark.ARRIVE);
    }

    private TripCrew retime(long id, MarkBody body, Mark mark) throws Exception {
        DbRec trip = requireMarkEditable(id, mark);
        OffsetDateTime time = body.time();
        if (time == null || time.isAfter(OffsetDateTime.now())) {
            throw ApiException.badRequest("Укажите время не позже текущего");
        }
        OffsetDateTime departed = trip.getDateTimeTZ("departed_dt");
        if (mark == Mark.ARRIVE && !time.isAfter(departed)) {
            throw ApiException.badRequest("Прибытие должно быть позже отправления");
        }
        DbRec change = changeAfter(id, trip, mark);
        if (change != null && !time.isBefore(change.getDateTimeTZ("dbeg"))) {
            throw ApiException.badRequest("Время должно быть раньше изменения вагона " + change.getString("number")
                    + " после отметки");
        }
        for (DbRec row : db.loadList("trip_wagon", Map.of("trip", id))) {
            WagonJournal.Opened opened = opened(row, mark);
            if (opened != null) {
                wagonJournal.retime(opened, time);
            }
        }
        db.updateRec("trip", Map.of("id", id, mark.time, time));
        evictCrew(load(id));
        return load(id);
    }

    private TripCrew cancel(long id, Mark mark) throws Exception {
        DbRec trip = requireMarkEditable(id, mark);
        DbRec change = changeAfter(id, trip, mark);
        if (change != null) {
            throw ApiException.conflict("После отметки " + mark.text + " у вагона " + change.getString("number")
                    + " были изменения (отцепка, прицепка или передача) - сначала отмените их");
        }
        for (DbRec row : db.loadList("trip_wagon", Map.of("trip", id))) {
            WagonJournal.Opened opened = opened(row, mark);
            if (opened == null) {
                continue;
            }
            // сначала ссылки строки состава: на удаляемые записи журналов стоит внешний ключ
            Map<String, Object> values = new HashMap<>();
            values.put("id", row.getLong("id"));
            values.put(mark.wagonColumns + "_wagon_location", null);
            values.put(mark.wagonColumns + "_wagon_status", null);
            db.updateRec("trip_wagon", values);
            wagonJournal.undo(opened);
        }
        Map<String, Object> values = new HashMap<>();
        values.put("id", id);
        values.put("trip_status_type", mark.statusBefore);
        values.put(mark.time, null);
        values.put(mark.by, null);
        values.put(mark.marked, null);
        db.updateRec("trip", values);
        evictCrew(load(id));
        return load(id);
    }

    /**
     * Рейс, отметку которого можно исправить или отменить: начальник поезда рейса (или администратор), отметка есть и
     * внесена не раньше {@link #MARK_EDIT_PERIOD} назад.
     */
    private DbRec requireMarkEditable(long id, Mark mark) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec trip = requireTrip(id);
        requireChief(id, me, "Исправляет и отменяет отметку " + mark.text + " начальник поезда этого рейса");
        OffsetDateTime marked = trip.getDateTimeTZ(mark.marked);
        if (trip.getLong("trip_status_type") != mark.status || marked == null) {
            throw ApiException.conflict("Отметки " + mark.text + " нет");
        }
        if (OffsetDateTime.now().isAfter(marked.plus(MARK_EDIT_PERIOD))) {
            throw ApiException.conflict("Исправить или отменить отметку можно в течение 10 минут после неё");
        }
        return trip;
    }

    /** Первое изменение вагонов рейса после отметки; нет - null. */
    private DbRec changeAfter(long id, DbRec trip, Mark mark) throws Exception {
        return db.loadSqlRec(TripSql.changesAfter(mark.wagonColumns),
                Map.of("trip", id, "since", trip.getDateTimeTZ(mark.time)));
    }

    /** Записи журналов вагона, открытые отметкой; отметка вагон не переводила - null. */
    private static WagonJournal.Opened opened(DbRec row, Mark mark) {
        Long location = row.getLong(mark.wagonColumns + "_wagon_location", null);
        Long status = row.getLong(mark.wagonColumns + "_wagon_status", null);
        return location == null || status == null ? null : new WagonJournal.Opened(location, status);
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
        if (crew.getWagons().isEmpty()) {
            throw ApiException.conflict("Состав рейса не сформирован");
        }
        Long station = crew.getTrip().getDepartureStation();
        List<String> away = crew.getWagons().stream()
                .filter(w -> w.getLocationType() != LocationType.STATION || station == null || w.getPlace() != station)
                .map(TripCrew.Wagon::getNumber)
                .toList();
        if (!away.isEmpty()) {
            throw ApiException.conflict("Вагоны не на станции отправления: " + String.join(", ", away));
        }
        boolean chief = crew.getCrew().stream().anyMatch(m -> m.getCrewDutyType() == CrewDuty.LNP);
        boolean pem = crew.getCrew().stream().anyMatch(m -> m.getCrewDutyType() == CrewDuty.PEM);
        if (!chief || !pem) {
            throw ApiException.conflict("Не назначены начальник поезда и ПЭМ");
        }
        List<String> notOk = TripNorm.notOk(crew.getWagons(), crew.getCrew());
        if (!notOk.isEmpty()) {
            throw ApiException.conflict("Не выполнена норма проводников: " + String.join(", ", notOk));
        }
    }

    TripCrew load(long id) throws Exception {
        DbRec tripRec = tripRec(id);
        TripCrew.Trip trip = DbRecMapper.toObject(tripRec, TripCrew.Trip.class);
        List<Long> stops = RouteService.stops(
                routeService.stations(List.of(trip.getRoute())).getOrDefault(trip.getRoute(), List.of()));
        trip.setStops(stops);
        trip.setDepartureStation(stops.isEmpty() ? null : stops.get(0));
        trip.setArrivalStation(stops.isEmpty() ? null : stops.get(stops.size() - 1));

        Map<Long, String> numbers = new HashMap<>();
        List<TripCrew.Wagon> wagons = new ArrayList<>();
        for (DbRec r : composition(id)) {
            numbers.put(r.getLong("wagon"), r.getString("number"));
            TripCrew.Wagon wagon = DbRecMapper.toObject(r, TripCrew.Wagon.class);
            wagon.setPassenger(!NOT_PASSENGER.contains(r.getString("code")));
            wagon.setDetached(detached(r));
            wagons.add(wagon);
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
            TripCrew.Member member = DbRecMapper.toObject(r, TripCrew.Member.class);
            member.setWagons(memberWagons);
            crew.add(member);
        }

        TripCrew res = new TripCrew();
        res.setTrip(trip);
        res.setWagons(wagons);
        res.setCrew(crew);
        res.setActs(acts(id));
        refResolver.resolve(res);
        return res;
    }

    private List<TripCrew.Act> acts(long id) throws Exception {
        Map<Long, List<String>> wagons = new HashMap<>();
        for (DbRec r : db.loadSql(TripSql.actWagons(), Map.of("trip", id))) {
            wagons.computeIfAbsent(r.getLong("coupling_act"), k -> new ArrayList<>()).add(r.getString("number"));
        }
        List<TripCrew.Act> res = db.loadSql(TripSql.acts(), Map.of("trip", id), TripCrew.Act.class);
        res.forEach(act -> act.setWagons(wagons.getOrDefault(act.getId(), List.of())));
        return res;
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
                        + other.getString("name") + " до " + other.getDateTimeTZ("arrival")
                        .atZoneSameInstant(ZoneId.systemDefault()).format(BUSY_UNTIL));
            }
        }
    }

    private Map<Long, List<DbRec>> otherTrips(List<Long> usrs, long trip) throws Exception {
        return UtDb.collectGroupBy_records(
                db.loadSql(TripSql.otherTrips(), Map.of("usrs", usrs, "trip", trip, "states", BUSY)), "usr");
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

    private void evictCrew(TripCrew crew) {
        crew.getCrew().forEach(m -> currentUserService.evict(m.getUsr().getId()));
    }

}
