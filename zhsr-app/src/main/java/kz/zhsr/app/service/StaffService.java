package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.constants.WagonResponsibleKind;
import kz.zhsr.app.constants.WagonResponsiblePlanStatus;
import kz.zhsr.app.constants.WagonResponsibleTripPoint;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.WagonRef;
import kz.zhsr.app.dto.staff.StaffFilter;
import kz.zhsr.app.dto.staff.StaffRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Сотрудники: список с тем, где сотрудник сейчас и на какой рейс назначен; перемещение в другое депо и смена категории
 * проводника. Доступно планировщику вагонов, планировщику проводников и администратору.
 * <p>
 * Депо и категория - периоды (usr_depot, usr_category): новое значение закрывает текущий период временем изменения и
 * открывает новый с этого времени.
 */
@Service
public class StaffService {

    // часы браузера и сервера расходятся: столько будущего считается погрешностью
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    /**
     * Новое значение периода сотрудника.
     *
     * @param value id депо (depot) или категории (category_type)
     * @param time  время изменения, ISO с часовым поясом; null - сейчас
     */
    public record PeriodBody(Long value, OffsetDateTime time) {
    }

    private final Db db;
    private final CurrentUserService currentUserService;
    private final RefResolver refResolver;

    public StaffService(Db db, CurrentUserService currentUserService, RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.refResolver = refResolver;
    }

    /**
     * Сотрудники по фильтру. Отключённых видит только администратор; логин - тоже.
     */
    public List<StaffRow> list(StaffFilter filter, ListSort sort) throws Exception {
        CurrentUser me = requirePlanner();
        boolean admin = me.hasRole(RoleType.ADMIN);
        SqlQuery q = StaffSql.list(filter, sort, admin, null);
        List<StaffRow> rows = db.loadSql(q.sql(), q.params(), StaffRow.class);
        if (!admin) {
            rows.forEach(r -> r.setLogin(null));
        }
        addDetails(rows);
        return rows;
    }

    /**
     * Переместить сотрудника в другое депо (на другую станцию). Сотрудника в рейсе не переместить.
     */
    @Transactional
    public StaffRow moveDepot(long id, PeriodBody body) throws Exception {
        requirePlanner();
        requireUsr(id);
        if (body.value() == null || db.loadRec("depot", body.value()) == null) {
            throw ApiException.badRequest("Выберите новое расположение");
        }
        if (!db.loadSql(StaffSql.activeTrips(), Map.of("usrs", List.of(id), "states",
                List.of(TripStatus.IN_PROGRESS))).isEmpty()) {
            throw ApiException.conflict("Сотрудник в рейсе: переместить его можно после прибытия");
        }
        setPeriod(id, "usr_depot", "depot", body.value(), body.time(), "Сотрудник уже в этом депо");
        currentUserService.evict(id);
        return row(id);
    }

    /**
     * Сменить категорию проводника.
     */
    @Transactional
    public StaffRow changeCategory(long id, PeriodBody body) throws Exception {
        requirePlanner();
        requireUsr(id);
        if (body.value() == null || db.loadRec("category_type", body.value()) == null) {
            throw ApiException.badRequest("Выберите новую категорию");
        }
        setPeriod(id, "usr_category", "category_type", body.value(), body.time(), "У сотрудника уже эта категория");
        currentUserService.evict(id);
        return row(id);
    }

    // --- внутреннее

    private CurrentUser requirePlanner() {
        CurrentUser me = currentUserService.get();
        if (!me.hasRole(RoleType.ADMIN) && !me.hasRole(RoleType.WAGON_PLANNER) && !me.hasRole(RoleType.CREW_PLANNER)) {
            throw ApiException.forbidden("Сотрудники доступны планировщикам и администратору");
        }
        return me;
    }

    private void requireUsr(long id) throws Exception {
        if (db.loadRec("usr", id) == null) {
            throw ApiException.notFound("Сотрудник не найден");
        }
    }

    private StaffRow row(long id) throws Exception {
        CurrentUser me = currentUserService.get();
        SqlQuery q = StaffSql.list(new StaffFilter(null, null, null, null), new ListSort(null, false), true, id);
        List<StaffRow> res = db.loadSql(q.sql(), q.params(), StaffRow.class);
        if (res.isEmpty()) {
            throw ApiException.notFound("Сотрудник не найден");
        }
        if (!me.hasRole(RoleType.ADMIN)) {
            res.forEach(r -> r.setLogin(null));
        }
        addDetails(res);
        return res.get(0);
    }

    /**
     * Период сотрудника (usr_depot, usr_category) со значением value с времени time: текущий период закрывается этим
     * временем. Время - не в будущем и не раньше начала текущего периода.
     */
    private void setPeriod(long usr, String table, String column, long value, OffsetDateTime time, String same)
            throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime at = time == null ? now : time;
        if (at.isAfter(now.plus(CLOCK_SKEW))) {
            throw ApiException.badRequest("Время изменения не может быть в будущем");
        }
        if (at.isAfter(now)) {
            at = now;
        }
        Map<String, Object> open = new HashMap<>();
        open.put("usr", usr);
        open.put("dend", null);
        DbRec current = db.loadRec(table, open);
        if (current != null) {
            if (Objects.equals(current.getLong(column), value)) {
                throw ApiException.badRequest(same);
            }
            if (!at.isAfter(current.getDateTimeTZ("dbeg"))) {
                throw ApiException.badRequest("Время изменения должно быть позже начала текущего периода");
            }
        }
        try {
            if (current != null) {
                db.updateRec(table, Map.of("id", current.getLong("id"), "dend", at));
            }
            db.insertRec(table, Map.of("usr", usr, column, value, "dbeg", at));
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    /**
     * Второй проход: роли, рейс и вагоны по факту, ближайший плановый рейс и его вагоны по плану.
     */
    private void addDetails(List<StaffRow> rows) throws Exception {
        if (rows.isEmpty()) {
            return;
        }
        List<Long> usrs = rows.stream().map(StaffRow::getId).toList();
        Map<Long, List<Long>> roles = new HashMap<>();
        for (DbRec r : db.loadList("usr_role", Map.of("usr", usrs))) {
            roles.computeIfAbsent(r.getLong("usr"), k -> new ArrayList<>()).add(r.getLong("role_type"));
        }

        // рейсы по плановому отправлению: выполняющийся - факт, первый плановый - назначение
        Map<Long, DbRec> fact = new HashMap<>();
        Map<Long, DbRec> plan = new HashMap<>();
        for (DbRec r : db.loadSql(StaffSql.activeTrips(), Map.of("usrs", usrs,
                "states", List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS)))) {
            Map<Long, DbRec> target = r.getLong("trip_status_type") == TripStatus.IN_PROGRESS ? fact : plan;
            target.putIfAbsent(r.getLong("usr"), r);
        }

        Map<Long, List<WagonRef>> factWagons = new HashMap<>();
        for (DbRec r : db.loadSql(StaffSql.factWagons(), Map.of("usrs", usrs, "fact", WagonResponsibleKind.FACT))) {
            factWagons.computeIfAbsent(r.getLong("usr"), k -> new ArrayList<>()).add(new WagonRef(r.getLong("wagon")));
        }

        Map<Long, List<WagonRef>> planWagons = new HashMap<>();
        List<Long> planTrips = plan.values().stream().map(r -> r.getLong("trip")).distinct().toList();
        if (!planTrips.isEmpty()) {
            Map<String, Object> params = new HashMap<>();
            params.put("usrs", usrs);
            params.put("trips", planTrips);
            params.put("plan", WagonResponsibleKind.PLAN);
            params.put("waiting", WagonResponsiblePlanStatus.WAITING);
            params.put("departure", WagonResponsibleTripPoint.DEPARTURE);
            for (DbRec r : db.loadSql(StaffSql.planWagons(), params)) {
                DbRec trip = plan.get(r.getLong("usr"));
                if (trip != null && trip.getLong("trip") == r.getLong("trip")) {
                    planWagons.computeIfAbsent(r.getLong("usr"), k -> new ArrayList<>())
                            .add(new WagonRef(r.getLong("wagon")));
                }
            }
        }

        for (StaffRow row : rows) {
            long usr = row.getId();
            List<Long> userRoles = roles.getOrDefault(usr, new ArrayList<>());
            userRoles.sort(null);
            row.setRoles(userRoles);
            DbRec f = fact.get(usr);
            if (f != null) {
                row.setFactTrip(new TripRef(f.getLong("trip")));
                row.setFactDuty(f.getLong("crew_duty_type"));
            }
            row.setFactWagons(factWagons.getOrDefault(usr, List.of()));
            DbRec p = plan.get(usr);
            if (p != null) {
                row.setPlanTrip(new TripRef(p.getLong("trip")));
                row.setPlanDuty(p.getLong("crew_duty_type"));
                row.setPlanWagons(planWagons.getOrDefault(usr, List.of()));
            }
        }
        refResolver.resolve(rows);
    }

}
