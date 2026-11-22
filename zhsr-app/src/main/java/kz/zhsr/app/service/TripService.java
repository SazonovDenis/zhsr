package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.kis.kisdbtools.repository.DbRecMapper;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.ConductorCategory;
import kz.zhsr.app.constants.CrewDuty;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.TrainWagonKind;
import kz.zhsr.app.constants.TrainWagonPlanStatus;
import kz.zhsr.app.constants.TripResponsibleLayer;
import kz.zhsr.app.constants.WagonResponsibleTripPoint;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.constants.WagonResponsibleKind;
import kz.zhsr.app.constants.WagonStatus;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.train.TrainChange;
import kz.zhsr.app.dto.trip.ConductorCandidate;
import kz.zhsr.app.dto.trip.StaffCandidate;
import kz.zhsr.app.dto.trip.TripCrew;
import kz.zhsr.app.dto.trip.TripResponsibles;
import kz.zhsr.app.dto.trip.TripFilter;
import kz.zhsr.app.dto.trip.TripRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Рейс: создание (Ф-РЕЙ-01), правка (Ф-РЕЙ-07), отмена (Ф-РЕЙ-05), вагоны поезда и бригада (Ф-РЕЙ-02, Ф-РЕЙ-06),
 * отправление (Ф-РЕЙ-03) и прибытие (Ф-РЕЙ-04).
 * <p>
 * Права: рейс создают, исправляют и отменяют планировщик вагонов и администратор. В рейсе в статусе «план»
 * начальника поезда и ПЭМ меняют планировщик вагонов и администратор, список проводников - планировщик проводников и
 * администратор. Распределение по вагонам - ответственные на рейс в журнале ответственных ({@link
 * TripResponsibleService}): в статусе «план» - план на рейс (планировщик проводников и начальник поезда этого рейса),
 * в статусе «выполняется» - факт (начальник поезда этого рейса); администратор - всегда. Отправление и прибытие
 * отмечает начальник поезда этого рейса. Администратор может всё. Смотреть формирование может тот, кому рейс виден в
 * списке рейсов. Вагоны поезда меняет {@link TrainService}.
 * <p>
 * Отправление и прибытие закрывают текущие записи журналов вагонов поезда и открывают новые в одной транзакции с кэшем
 * wagon_info. Записи, открытые отметкой, - текущие записи вагонов поезда с dbeg, равным времени отметки.
 */
@Service
public class TripService {

    // типы вагонов (wagon_type.code), для которых норма проводников не требуется
    static final Set<String> NOT_PASSENGER = Set.of("RESTAURANT", "STAFF");

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
     * @param train     поезд (train); null - поезд не назначен
     */
    public record TripBody(Long route, OffsetDateTime plannedDt, Long train) {
    }

    /**
     * Назначение на должность рейса.
     *
     * @param usr usr.id; null - снять с должности
     */
    public record DutyBody(Long usr) {
    }

    /**
     * Поездная бригада рейса.
     *
     * @param lnp        начальник поезда (usr.id); null - не назначен
     * @param pem        ПЭМ (usr.id); null - не назначен
     * @param conductors проводники (usr.id); пусто - проводников нет
     */
    public record CrewBody(Long lnp, Long pem, List<Long> conductors) {
    }

    /**
     * Отметка отправления или прибытия.
     *
     * @param time         фактическое время, ISO с часовым поясом; null - сейчас
     * @param responsibles ответственные за вагоны, сохранённые в окне отметки: записываются временем отметки у вагонов,
     *                     где отличаются от текущих; null - не менять. Исправление времени их не использует
     */
    public record MarkBody(OffsetDateTime time, List<TripResponsibleService.WagonUsrs> responsibles) {
    }

    /**
     * Ответственные за вагоны рейса (Ф-РЕЙ-08).
     *
     * @param layer  что меняется: TripResponsibleLayer.TRIP, AFTER или FACT; null - TRIP
     * @param time   время изменения факта, ISO с часовым поясом; null - сейчас; у плана не используется
     * @param wagons ответственные изменённых вагонов
     */
    public record ResponsiblesBody(Integer layer, OffsetDateTime time,
                                   List<TripResponsibleService.WagonUsrs> wagons) {
    }

    /**
     * Распределение бригады по вагонам: ответственные на рейс.
     *
     * @param time    время изменения у выполняющегося рейса, ISO с часовым поясом; null - сейчас; до отправления не
     *                используется - меняется план на рейс
     * @param members вагоны сотрудников бригады
     */
    public record CrewWagonsBody(OffsetDateTime time, List<MemberWagons> members) {
    }

    /**
     * Вагоны сотрудника бригады.
     *
     * @param usr    сотрудник (usr.id)
     * @param wagons номера вагонов: один или два соседних; пусто - сотрудник без вагона
     */
    public record MemberWagons(long usr, List<String> wagons) {
    }

    private final Db db;
    private final CurrentUserService currentUserService;
    private final Visibility visibility;
    private final RouteService routeService;
    private final ListService listService;
    private final WagonJournal wagonJournal;
    private final TripResponsibleService tripResponsibleService;
    private final WagonRun wagonRun;
    private final TrainService trainService;
    private final TrainJournal trainJournal;
    private final RefResolver refResolver;

    public TripService(Db db, CurrentUserService currentUserService, Visibility visibility, RouteService routeService,
                       ListService listService, WagonJournal wagonJournal,
                       TripResponsibleService tripResponsibleService,
                       WagonRun wagonRun, TrainService trainService, TrainJournal trainJournal,
                       RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.visibility = visibility;
        this.routeService = routeService;
        this.listService = listService;
        this.wagonJournal = wagonJournal;
        this.tripResponsibleService = tripResponsibleService;
        this.wagonRun = wagonRun;
        this.trainService = trainService;
        this.trainJournal = trainJournal;
        this.refResolver = refResolver;
    }

    /**
     * Создать рейс в статусе «план» (Ф-РЕЙ-01). Создают планировщик вагонов и администратор.
     */
    @Transactional
    public TripRow create(TripBody body) throws Exception {
        requirePlanner();
        Map<String, Object> values = tripValues(body, null);
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
     * Удалить рейс (DELETE /trips/{id}): планировщик вагонов и администратор. Бригада рейса удаляется каскадом;
     * на рейс ссылаются вагоны, заявки, чек-листы... - удалить не даст внешний ключ (сообщение - FkMessages).
     */
    @Transactional
    public void delete(long id) throws Exception {
        requireTrip(id);
        requirePlanner();
        try {
            cancelResponsiblePlans(load(id));
            db.deleteRec("trip", id);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    /**
     * Исправить маршрут, поезд и плановое отправление рейса в статусе «план» (Ф-РЕЙ-07). Исправляют планировщик
     * вагонов и администратор. Планы ответственных рейса - на рейс и после рейса - связаны с рейсом ссылкой: их
     * справочное начало переносится на новые плановые отправление и прибытие; при смене поезда они отменяются: бригада
     * остаётся без вагонов.
     */
    @Transactional
    public TripRow update(long id, TripBody body) throws Exception {
        DbRec trip = requireTrip(id);
        requirePlanner();
        if (trip.getLong("trip_status_type") != TripStatus.PLANNED) {
            throw ApiException.conflict("Рейс исправляется только в статусе «План»");
        }
        Map<String, Object> values = tripValues(body, id);
        values.put("id", id);
        try {
            db.updateRec("trip", values);
            if (Objects.equals(trip.getLong("train", null), body.train())) {
                tripResponsibleService.retimePlans(load(id));
            } else {
                tripResponsibleService.cancelPlans(id);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        evictCrew(load(id));
        return row(id);
    }

    /**
     * Отменить рейс в статусе «план» (Ф-РЕЙ-05): расположение вагонов не меняется. Отменяют планировщик вагонов и
     * администратор.
     */
    @Transactional
    public TripRow cancel(long id) throws Exception {
        DbRec trip = requireTrip(id);
        if (!isWagonPlanner(currentUserService.get())) {
            throw ApiException.forbidden("Рейс отменяет планировщик вагонов");
        }
        if (trip.getLong("trip_status_type") != TripStatus.PLANNED) {
            throw ApiException.conflict("Отменяется только рейс в статусе «План»");
        }
        cancelResponsiblePlans(load(id));
        db.updateRec("trip", Map.of("id", id, "trip_status_type", TripStatus.CANCELLED));
        evictCrew(load(id));
        return row(id);
    }

    /**
     * Рейс, вагоны поезда, бригада и прицепки и отцепки (GET /trips/{id}/crew).
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
     * Поездная бригада рейса: начальник поезда, ПЭМ и проводники одной операцией. Начальника поезда и ПЭМ меняет
     * планировщик вагонов, проводников - планировщик проводников; то, что не изменилось, право не проверяет. Проводник,
     * которого нет в списке, уходит из бригады и из планов ответственных рейса; новые проводники - нераспределённые.
     */
    @Transactional
    public TripCrew setCrew(long id, CrewBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec trip = requirePlanEdit(id, isWagonPlanner(me) || isCrewPlanner(me),
                "Поездную бригаду меняют планировщик вагонов и планировщик проводников");
        List<DbRec> crew = db.loadList("trip_crew", Map.of("trip", id));
        Set<Long> keep = new LinkedHashSet<>(body.conductors() == null ? List.of() : body.conductors());
        Set<Long> conductors = new HashSet<>();
        for (DbRec r : crew) {
            if (r.getLong("crew_duty_type") == CrewDuty.CONDUCTOR) {
                conductors.add(r.getLong("usr"));
            }
        }
        boolean dutiesChanged = !Objects.equals(dutyUsr(crew, CrewDuty.LNP), body.lnp())
                || !Objects.equals(dutyUsr(crew, CrewDuty.PEM), body.pem());
        if (dutiesChanged && !isWagonPlanner(me)) {
            throw ApiException.forbidden("Начальника поезда и ПЭМ назначает планировщик вагонов");
        }
        if (!conductors.equals(keep) && !isCrewPlanner(me)) {
            throw ApiException.forbidden("Проводников рейса набирает планировщик проводников");
        }
        Set<Long> removed = new HashSet<>();
        Set<Long> changed = new HashSet<>();
        try {
            // сначала уходят снятые проводники: сотрудник может перейти из проводников в начальники поезда или ПЭМ
            for (DbRec r : crew) {
                long usr = r.getLong("usr");
                if (r.getLong("crew_duty_type") == CrewDuty.CONDUCTOR && !keep.contains(usr)) {
                    db.deleteRec("trip_crew", r.getLong("id"));
                    removed.add(usr);
                    changed.add(usr);
                }
            }
            changed.addAll(applyDuty(trip, CrewDuty.LNP, body.lnp(), removed));
            changed.addAll(applyDuty(trip, CrewDuty.PEM, body.pem(), removed));
            Set<Long> present = new HashSet<>();
            for (DbRec r : db.loadList("trip_crew", Map.of("trip", id))) {
                present.add(r.getLong("usr"));
            }
            for (long usr : keep) {
                if (present.contains(usr)) {
                    if (!conductors.contains(usr)) {
                        throw ApiException.conflict("Сотрудник уже в бригаде рейса");
                    }
                    continue;
                }
                requireRole(usr, RoleType.CONDUCTOR);
                requireFree(trip, usr);
                db.insertRec("trip_crew", Map.of("trip", id, "usr", usr, "crew_duty_type", CrewDuty.CONDUCTOR));
                changed.add(usr);
            }
            tripResponsibleService.dropFromPlans(load(id), removed);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        changed.forEach(currentUserService::evict);
        return load(id);
    }

    /** Сотрудник на должности duty в бригаде crew; null - должность не занята. */
    private static Long dutyUsr(List<DbRec> crew, long duty) {
        return crew.stream().filter(r -> r.getLong("crew_duty_type") == duty).map(r -> r.getLong("usr"))
                .findFirst().orElse(null);
    }

    /**
     * Назначить на должность начальника поезда или ПЭМ сотрудника user (null - снять с должности) без проверки прав.
     * Снятый с должности попадает в removed - его убирают из планов ответственных.
     *
     * @return сотрудники, чьё дежурство изменилось
     */
    private Set<Long> applyDuty(DbRec trip, long duty, Long user, Set<Long> removed) throws Exception {
        long id = trip.getLong("id");
        DbRec current = db.loadRec("trip_crew", Map.of("trip", id, "crew_duty_type", duty));
        if (current != null && user != null && current.getLong("usr") == user) {
            return Set.of();
        }
        Set<Long> changed = new HashSet<>();
        if (current != null) {
            db.deleteRec("trip_crew", current.getLong("id"));
            removed.add(current.getLong("usr"));
            changed.add(current.getLong("usr"));
        }
        if (user != null) {
            if (!db.loadList("trip_crew", Map.of("trip", id, "usr", user)).isEmpty()) {
                throw ApiException.conflict("Сотрудник уже в бригаде рейса");
            }
            requireRole(user, roleOf(duty));
            requireFree(trip, user);
            db.insertRec("trip_crew", Map.of("trip", id, "usr", user, "crew_duty_type", duty));
            removed.remove(user);
            changed.add(user);
        }
        return changed;
    }

    /**
     * Распределить бригаду по вагонам: у каждого сотрудника - один вагон или два соседних; пустой список - сотрудник
     * без вагона. Рейс в статусе «план» - меняется план ответственных на рейс, «выполняется» - факт временем
     * body.time. Состав вагона - сотрудники, которых распределение на нём оставило, и другие его ответственные.
     */
    @Transactional
    public TripCrew setCrewWagons(long id, CrewWagonsBody body) throws Exception {
        DbRec trip = requireTrip(id);
        CurrentUser me = currentUserService.get();
        long state = trip.getLong("trip_status_type");
        boolean planner = isCrewPlanner(me);
        boolean admin = me.hasRole(RoleType.ADMIN);
        boolean allowed = (state == TripStatus.PLANNED && (planner || isChief(id, me)))
                || (state == TripStatus.IN_PROGRESS && (admin || isChief(id, me)));
        if (!allowed) {
            throw ApiException.forbidden(state == TripStatus.PLANNED || state == TripStatus.IN_PROGRESS
                    ? "Распределяет проводников планировщик проводников или начальник поезда рейса"
                    : "Рейс закончен");
        }
        TripCrew crew = load(id);
        List<TripCrew.Wagon> riding = crew.getWagons().stream().filter(TripCrew.Wagon::isRiding).toList();
        List<String> order = riding.stream().map(TripCrew.Wagon::getNumber).toList();
        List<Long> ids = riding.stream().map(TripCrew.Wagon::getWagon).toList();
        Map<Long, List<String>> targets = new LinkedHashMap<>();
        for (MemberWagons m : body.members() == null ? List.<MemberWagons>of() : body.members()) {
            if (crew.getCrew().stream().noneMatch(c -> c.getUsr().getId() == m.usr())) {
                throw ApiException.notFound("Сотрудника нет в бригаде рейса");
            }
            List<String> list = new ArrayList<>(new LinkedHashSet<>(m.wagons() == null ? List.of() : m.wagons()));
            if (list.size() > 2) {
                throw ApiException.badRequest("Проводник обслуживает один вагон или два соседних");
            }
            if (!order.containsAll(list)) {
                throw ApiException.badRequest("Вагона нет в поезде рейса");
            }
            if (list.size() == 2 && Math.abs(order.indexOf(list.get(0)) - order.indexOf(list.get(1))) != 1) {
                throw ApiException.badRequest("Вторым вагоном можно выбрать только соседний");
            }
            targets.put(m.usr(), list);
        }
        // ответственные на рейс вагонов: до отправления - план на рейс, в пути - текущий факт
        Map<Long, List<Long>> was = new HashMap<>();
        if (state == TripStatus.PLANNED) {
            for (Long wagon : ids) {
                List<Long> plan = wagonJournal.waitingPlanUsrs(wagon, crew.getTrip().getId(),
                        WagonResponsibleTripPoint.DEPARTURE);
                was.put(wagon, plan == null ? List.of() : plan);
            }
        } else {
            was.putAll(wagonJournal.currentUsrs(ids));
        }
        List<TripResponsibleService.WagonUsrs> changes = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            long wagon = ids.get(i);
            String number = order.get(i);
            List<Long> usrs = new ArrayList<>(was.getOrDefault(wagon, List.of()));
            for (Map.Entry<Long, List<String>> t : targets.entrySet()) {
                usrs.remove(t.getKey());
                if (t.getValue().contains(number)) {
                    usrs.add(t.getKey());
                }
            }
            if (!WagonJournal.sameUsrs(usrs, was.getOrDefault(wagon, List.of()))) {
                changes.add(new TripResponsibleService.WagonUsrs(wagon, usrs));
            }
        }
        if (changes.isEmpty()) {
            return crew;
        }
        OffsetDateTime time = state == TripStatus.PLANNED ? null : ActTime.check(body.time());
        try {
            tripResponsibleService.save(crew, TripResponsibleLayer.TRIP, time, changes);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        targets.keySet().forEach(currentUserService::evict);
        return load(id);
    }

    /**
     * Назначить начальника поезда или ПЭМ; user пуст - снять с должности.
     */
    @Transactional
    public TripCrew setDuty(long id, long duty, DutyBody body) throws Exception {
        DbRec trip = requirePlanEdit(id, isWagonPlanner(currentUserService.get()),
                "Начальника поезда и ПЭМ назначает планировщик вагонов");
        long role = roleOf(duty);
        Long user = body.usr();
        DbRec current = db.loadRec("trip_crew", Map.of("trip", id, "crew_duty_type", duty));
        if (current != null && user != null && current.getLong("usr") == user) {
            return load(id);
        }
        try {
            if (current != null) {
                db.deleteRec("trip_crew", current.getLong("id"));
                tripResponsibleService.dropFromPlans(load(id), Set.of(current.getLong("usr")));
                currentUserService.evict(current.getLong("usr"));
            }
            if (user != null) {
                if (!db.loadList("trip_crew", Map.of("trip", id, "usr", user)).isEmpty()) {
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
     * Отметить отправление (Ф-РЕЙ-03): рейс - «выполняется», вагоны поезда - «в рейсе» и «в пути», бригада заступает
     * на дежурство. Отработка плана ответственных на рейс: ответственные вагонов временем отправления - по плану или
     * по правке в окне отправления, вагон без плана - без ответственных; план - «отработан».
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
        OffsetDateTime lastFact = trainJournal.lastFact(crew.getTrip().getTrain().getId());
        if (lastFact != null && !time.isAfter(lastFact)) {
            throw ApiException.badRequest("Отправление должно быть позже последней прицепки или отцепки поезда");
        }
        db.updateRec("trip", Map.of("id", id, "trip_status_type", TripStatus.IN_PROGRESS, "departed_dt", time,
                "departed_by_usr", me.id(), "departure_marked_dt", OffsetDateTime.now()));
        for (TripCrew.Wagon w : crew.getWagons()) {
            if (w.isRiding()) {
                wagonJournal.moveKeepResponsible(w.getWagon(), time, LocationType.TRIP, null, id,
                        WagonStatus.IN_TRIP);
            }
        }
        db.updateRec("trip_crew", Map.of("dbeg", time), nullParam("trip", id, "dbeg"));
        tripResponsibleService.markDeparture(load(id), time, body.responsibles());
        evictCrew(crew);
        return load(id);
    }

    /**
     * Отметить прибытие (Ф-РЕЙ-04): рейс - «выполнен», вагоны, которые едут в поезде, - на станции прибытия и «в
     * простое»; отработка плана ответственных после рейса: ответственные временем прибытия - по плану или по правке в
     * окне прибытия, вагон без плана и правки не меняется; вносятся участки пробега, бригада заканчивает дежурство.
     * Вагоны остаются в поезде. Вагоны, отцепленные в пути, не меняются.
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
        if (crew.getChanges().stream().anyMatch(c -> c.getKind() == TrainWagonKind.PLAN)) {
            throw ApiException.conflict("Отметьте или отмените плановые прицепки и отцепки рейса");
        }
        Long station = crew.getTrip().getArrivalStation();
        if (station == null) {
            throw ApiException.conflict("У маршрута рейса нет станции прибытия");
        }

        db.updateRec("trip", Map.of("id", id, "trip_status_type", TripStatus.DONE, "arrived_dt", time,
                "arrived_by_usr", me.id(), "arrival_marked_dt", OffsetDateTime.now()));
        for (TripCrew.Wagon w : crew.getWagons()) {
            if (!w.isRiding()) {
                continue;
            }
            wagonJournal.moveKeepResponsible(w.getWagon(), time, LocationType.STATION, station, null,
                    WagonStatus.IN_IDLE);
            Long attach = trainJournal.attachOf(crew.getTrip().getTrain().getId(), w.getWagon(), time);
            if (attach != null) {
                wagonRun.addSegment(id, attach, null);
            }
        }
        db.updateRec("trip_crew", Map.of("dend", time), nullParam("trip", id, "dend"));
        tripResponsibleService.markArrival(load(id), time, body.responsibles());
        evictCrew(crew);
        return load(id);
    }

    /**
     * Ответственные за вагоны рейса (GET /trips/{id}/responsibles, Ф-РЕЙ-08): вагоны поезда рейса, их ответственные
     * слоя layer и кого можно назначить; readonlyReason - почему сотрудник не может менять.
     *
     * @param layer TripResponsibleLayer; null - TRIP
     */
    public TripResponsibles responsibles(long id, Integer layer) throws Exception {
        CurrentUser me = currentUserService.get();
        requireTrip(id);
        return tripResponsibleService.view(load(id), layerOf(layer), responsiblesReadonly(id, me));
    }

    /**
     * Сменить ответственных за вагоны рейса (POST /trips/{id}/responsibles, Ф-РЕЙ-08) у вагонов, где состав отличается
     * от показанного: план на рейс (до отправления) и план после рейса - планом, ответственных на рейс в пути и после
     * прибытия - фактом одним временем. Меняют начальник поезда рейса, планировщики вагонов и проводников,
     * администратор.
     *
     * @throws ApiException 403 - нет права; 409 - слой не меняется в этом статусе рейса; 400 - время, вагон или
     *                      сотрудник неверны
     */
    @Transactional
    public TripResponsibles saveResponsibles(long id, ResponsiblesBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        requireTrip(id);
        String reason = responsiblesReadonly(id, me);
        if (reason != null) {
            throw ApiException.forbidden(reason);
        }
        int layer = layerOf(body.layer());
        TripCrew crew = load(id);
        boolean fact = layer == TripResponsibleLayer.FACT
                || (layer == TripResponsibleLayer.TRIP && crew.getTrip().getTripStatusType() != TripStatus.PLANNED);
        OffsetDateTime time = fact ? ActTime.check(body.time()) : null;
        tripResponsibleService.save(crew, layer, time, body.wagons());
        evictCrew(crew);
        return tripResponsibleService.view(load(id), layer, null);
    }

    /** Почему сотрудник не может менять ответственных за вагоны рейса; может - null. Статус рейса - отдельно. */
    private String responsiblesReadonly(long id, CurrentUser me) throws Exception {
        boolean allowed = me.hasRole(RoleType.ADMIN) || me.hasRole(RoleType.WAGON_PLANNER)
                || me.hasRole(RoleType.CREW_PLANNER) || isChief(id, me);
        return allowed ? null : "Ответственных за вагоны меняют начальник поезда рейса и планировщики";
    }

    private static int layerOf(Integer layer) {
        if (layer == null) {
            return TripResponsibleLayer.TRIP;
        }
        if (layer != TripResponsibleLayer.TRIP && layer != TripResponsibleLayer.AFTER
                && layer != TripResponsibleLayer.ARRIVAL && layer != TripResponsibleLayer.FACT) {
            throw ApiException.badRequest("Неизвестный слой ответственных: " + layer
                    + "; сервер, возможно, не обновлён");
        }
        return layer;
    }

    /** Сколько после отметки отправления или прибытия её можно исправить или отменить. */
    public static final Duration MARK_EDIT_PERIOD = Duration.ofMinutes(10);

    /** Отметка отправления или прибытия: колонки рейса, статус рейса с отметкой и без неё. */
    private enum Mark {
        DEPART("departed_dt", "departed_by_usr", "departure_marked_dt", "dbeg", TripStatus.IN_PROGRESS,
                TripStatus.PLANNED, WagonResponsibleTripPoint.DEPARTURE, "отправления"),
        ARRIVE("arrived_dt", "arrived_by_usr", "arrival_marked_dt", "dend", TripStatus.DONE,
                TripStatus.IN_PROGRESS, WagonResponsibleTripPoint.ARRIVAL, "прибытия");

        final String time;
        final String by;
        final String marked;
        /** колонка trip_crew, которую ставит отметка */
        final String crewColumn;
        final long status;
        final long statusBefore;
        /** стадия рейса в журнале ответственных: WagonResponsibleTripPoint */
        final int point;
        final String text;

        Mark(String time, String by, String marked, String crewColumn, long status, long statusBefore, int point,
             String text) {
            this.time = time;
            this.by = by;
            this.marked = marked;
            this.crewColumn = crewColumn;
            this.status = status;
            this.statusBefore = statusBefore;
            this.point = point;
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
     * Рейс - снова «план», записи журналов вагонов, открытые отправлением, удаляются, бригада снова не на дежурстве,
     * план ответственных на рейс - снова «ожидает». Если после отправления были изменения (отцепка, прицепка, передача,
     * смена ответственных), отмены нет.
     */
    @Transactional
    public TripCrew cancelDeparture(long id) throws Exception {
        return cancel(id, Mark.DEPART);
    }

    /**
     * Отменить прибытие (Ф-РЕЙ-04): как {@link #cancelDeparture}; рейс - снова «выполняется», план ответственных после
     * рейса - снова «ожидает».
     */
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
            throw ApiException.badRequest("Время должно быть раньше изменения «" + change.getString("number")
                    + "» после отметки");
        }
        OffsetDateTime since = trip.getDateTimeTZ(mark.time);
        for (WagonJournal.Opened opened : opened(trip, mark)) {
            wagonJournal.retime(opened, time);
        }
        tripResponsibleService.retimeMarks(id, mark.point, trainWagons(trip), since, time);
        db.updateRec("trip_crew", Map.of(mark.crewColumn, time), Map.of("trip", id, mark.crewColumn, since));
        db.updateRec("trip", Map.of("id", id, mark.time, time));
        evictCrew(load(id));
        return load(id);
    }

    private TripCrew cancel(long id, Mark mark) throws Exception {
        DbRec trip = requireMarkEditable(id, mark);
        DbRec change = changeAfter(id, trip, mark);
        if (change != null) {
            throw ApiException.conflict("После отметки " + mark.text + " были изменения («" + change.getString("number")
                    + "»: отцепка, прицепка, передача или смена ответственных) - сначала отмените их");
        }
        OffsetDateTime since = trip.getDateTimeTZ(mark.time);
        if (mark == Mark.ARRIVE) {
            // отмена прибытия снимает участки пробега, внесённые прибытием: у вагонов, которые остались в поезде
            long train = trip.getLong("train");
            Map<String, Object> open = nullParam("train", train, "dend");
            open.put("kind", TrainWagonKind.FACT);
            for (DbRec row : db.loadList("train_wagon", open)) {
                Long attach = trainJournal.attachOf(train, row.getLong("wagon"), since);
                if (attach != null) {
                    wagonRun.removeSegment(id, attach);
                }
            }
        }
        for (WagonJournal.Opened opened : opened(trip, mark)) {
            wagonJournal.undo(opened);
        }
        // отработка плана ответственных отменяется: факты отметки удаляются, план - снова «ожидает»
        tripResponsibleService.unmark(id, mark.point, trainWagons(trip), since);
        Map<String, Object> crewValues = new HashMap<>();
        crewValues.put(mark.crewColumn, null);
        db.updateRec("trip_crew", crewValues, Map.of("trip", id, mark.crewColumn, since));
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
     * Записи журналов, открытые отметкой: текущие записи вагонов поезда с началом во время отметки. Отправление открыло
     * расположение «в рейсе» этого рейса, прибытие - расположение на станции.
     */
    private List<WagonJournal.Opened> opened(DbRec trip, Mark mark) throws Exception {
        OffsetDateTime since = trip.getDateTimeTZ(mark.time);
        List<Long> wagons = db.loadList("train_wagon", Map.of("train", trip.getLong("train"),
                        "kind", TrainWagonKind.FACT)).stream()
                .map(r -> r.getLong("wagon")).distinct().toList();
        List<WagonJournal.Opened> res = new ArrayList<>();
        if (wagons.isEmpty()) {
            return res;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("wagon", wagons);
        params.put("dbeg", since);
        if (mark == Mark.DEPART) {
            params.put("trip", trip.getLong("id"));
        } else {
            params.put("location_type", LocationType.STATION);
        }
        Map<Long, DbRec> statuses = UtDb.collectGroupBy_record(db.loadList("wagon_status",
                Map.of("wagon", wagons, "dbeg", since)), "wagon");
        for (DbRec location : db.loadList("wagon_location", params)) {
            long wagon = location.getLong("wagon");
            DbRec status = statuses.get(wagon);
            if (status == null) {
                continue;
            }
            res.add(new WagonJournal.Opened(location.getLong("id"), status.getLong("id"), null));
        }
        return res;
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

    /** Первое изменение после отметки; нет - null. */
    private DbRec changeAfter(long id, DbRec trip, Mark mark) throws Exception {
        return db.loadSqlRec(TripSql.changesAfter(), Map.of("train", trip.getLong("train"),
                "since", trip.getDateTimeTZ(mark.time), "fact", WagonResponsibleKind.FACT,
                "trainFact", TrainWagonKind.FACT));
    }

    // --- внутреннее

    /** Вагоны поезда рейса: все, кто был в поезде по факту, без повторов. */
    private List<Long> trainWagons(DbRec trip) throws Exception {
        Long train = trip.getLong("train", null);
        if (train == null) {
            return List.of();
        }
        return db.loadList("train_wagon", Map.of("train", train, "kind", TrainWagonKind.FACT)).stream()
                .map(r -> r.getLong("wagon")).distinct().toList();
    }

    /** Отменить планы ответственных рейса в статусе «план»: на рейс и после рейса. */
    private void cancelResponsiblePlans(TripCrew crew) throws Exception {
        if (crew.getTrip().getTripStatusType() != TripStatus.PLANNED) {
            return;
        }
        tripResponsibleService.cancelPlans(crew.getTrip().getId());
    }

    /**
     * Готовность к отправлению: поезд назначен и в нём есть вагоны, плановые прицепки и отцепки до отправления
     * отмечены, вагоны на станции отправления, начальник поезда и ПЭМ назначены, норма проводников выполнена.
     */
    private void requireReady(TripCrew crew) {
        if (crew.getTrip().getTrain() == null) {
            throw ApiException.conflict("Поезд рейса не назначен");
        }
        if (!crew.getPending().isEmpty()) {
            throw ApiException.conflict("Отметьте или отмените плановые прицепки и отцепки до отправления: "
                    + String.join(", ", crew.getPending().stream().map(c -> c.getWagon().getNumber()).toList()));
        }
        List<TripCrew.Wagon> riding = crew.getWagons().stream().filter(TripCrew.Wagon::isRiding).toList();
        if (riding.isEmpty()) {
            throw ApiException.conflict("В поезде рейса нет вагонов");
        }
        Long station = crew.getTrip().getDepartureStation();
        List<String> away = riding.stream()
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
        Long train = tripRec.getLong("train", null);
        trip.setTrain(train == null ? null : new TrainRef(train));
        List<DbRec> stopRecs = routeService.stations(List.of(trip.getRoute())).getOrDefault(trip.getRoute(), List.of())
                .stream().filter(rs -> rs.getBoolean("have_stop")).toList();
        List<Long> stops = stopRecs.stream().map(rs -> rs.getLong("station")).toList();
        trip.setStops(stops);
        trip.setRouteStations(stopRecs.stream().map(rs -> rs.getLong("id")).toList());
        trip.setStopTimes(stopTimes(stopRecs, trip.getPlannedDt()));
        trip.setDepartureStation(stops.isEmpty() ? null : stops.get(0));
        trip.setArrivalStation(stops.isEmpty() ? null : stops.get(stops.size() - 1));

        List<TrainChange> changes = trainService.tripChanges(id);
        List<TripCrew.Wagon> wagons = trainService.tripWagons(trip, changes);

        // вагоны сотрудников - ответственные на рейс в журнале ответственных, у вагонов, которые едут в поезде
        Map<Long, List<String>> crewWagons = tripResponsibleService.crewWagons(id,
                wagons.stream().filter(TripCrew.Wagon::isRiding).map(TripCrew.Wagon::getWagon).distinct().toList());
        Map<String, Object> params = new HashMap<>();
        params.put("trip", id);
        params.put("senior", ConductorCategory.SENIOR);
        params.put("arrived", trip.getArrivedDt());
        Map<Long, TripCrew.Member> members = new LinkedHashMap<>();
        for (DbRec r : db.loadSql(TripSql.crew(), params)) {
            TripCrew.Member member = DbRecMapper.toObject(r, TripCrew.Member.class);
            member.setWagons(crewWagons.getOrDefault(r.getLong("usr"), List.of()));
            members.putIfAbsent(r.getLong("usr"), member);
        }

        TripCrew res = new TripCrew();
        res.setTrip(trip);
        res.setWagons(wagons);
        res.setCrew(new ArrayList<>(members.values()));
        res.setChanges(changes);
        res.setCancelled(trainService.tripPlans(id, TrainWagonPlanStatus.CANCELLED));
        res.setDone(trainService.tripPlans(id, TrainWagonPlanStatus.DONE));
        res.setLastFactDt(train == null ? null : trainJournal.lastFact(train));
        res.setPending(train == null || trip.getTripStatusType() != TripStatus.PLANNED || stopRecs.isEmpty()
                ? List.of()
                : trainService.pendingBefore(train, id, stopRecs.get(0).getLong("id"), trip.getPlannedDt()));
        refResolver.resolve(res);
        return res;
    }

    /** Время стоянки на остановках по расписанию рейса: отправление, у конечной - прибытие. */
    private static List<OffsetDateTime> stopTimes(List<DbRec> stops, OffsetDateTime planned) {
        List<OffsetDateTime> res = new ArrayList<>();
        if (stops.isEmpty()) {
            return res;
        }
        DbRec first = stops.get(0);
        int base = first.get("departure_min") == null ? 0 : first.getInt("departure_min");
        for (DbRec rs : stops) {
            Integer minutes = rs.get("departure_min") != null ? Integer.valueOf(rs.getInt("departure_min"))
                    : rs.get("arrival_min") != null ? Integer.valueOf(rs.getInt("arrival_min")) : null;
            res.add(minutes == null ? planned : planned.plusMinutes(minutes - base));
        }
        return res;
    }

    private Map<String, Object> tripValues(TripBody body, Long id) throws Exception {
        if (body.route() == null || body.plannedDt() == null) {
            throw ApiException.badRequest("Укажите маршрут и плановое отправление");
        }
        DbRec route = db.loadRec("route", body.route());
        LocalDate end = route == null ? null : RouteService.localDate(route, "dend");
        // при правке рейса его прежний маршрут допустим и после окончания действия: маршрут не меняется
        boolean sameRoute = id != null && route != null
                && db.loadRec("trip", id).getLong("route") == body.route();
        if (route == null || (!sameRoute && end != null && end.isBefore(LocalDate.now()))) {
            throw ApiException.badRequest("Маршрут не действует");
        }
        if (body.train() != null) {
            DbRec train = trainService.requireTrain(body.train());
            if (train.get("dend") != null) {
                throw ApiException.badRequest("Поезд расформирован");
            }
            requireTrainFree(body.train(), body.route(), body.plannedDt(), id);
        }
        Map<String, Object> values = new HashMap<>();
        values.put("route", body.route());
        values.put("planned_dt", body.plannedDt());
        values.put("train", body.train());
        return values;
    }

    /** Поезд не идёт в другом рейсе в статусе «план» или «выполняется», пересекающемся по времени. */
    private void requireTrainFree(long train, long route, OffsetDateTime planned, Long except) throws Exception {
        DbRec duration = db.loadSqlRec(TripSql.routeDuration(), Map.of("route", route));
        OffsetDateTime end = planned.plusMinutes(duration.getInt("duration"));
        for (DbRec other : db.loadSql(TripSql.trainTrips(), Map.of("train", train, "states", ACTIVE))) {
            if (except != null && other.getLong("id") == except) {
                continue;
            }
            if (other.getDateTimeTZ("beg").isBefore(end) && other.getDateTimeTZ("fin").isAfter(planned)) {
                throw ApiException.conflict("Поезд занят в рейсе " + other.getString("train_number") + " "
                        + other.getString("name") + " " + other.getDateTimeTZ("beg")
                        .atZoneSameInstant(ZoneId.systemDefault()).format(BUSY_UNTIL));
            }
        }
    }

    private TripRow row(long id) throws Exception {
        return listService.trips(new TripFilter(id, null, null, null, null, null, null, null), new ListSort(null, false)).get(0);
    }

    private void requirePlanner() {
        if (!isWagonPlanner(currentUserService.get())) {
            throw ApiException.forbidden("Рейс создаёт и исправляет планировщик вагонов");
        }
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
        SqlQuery q = ListSql.trips(new TripFilter(id, null, null, null, null, null, null, null), access,
                new ListSort(null, false));
        if (db.loadSql(q.sql(), q.params()).isEmpty()) {
            throw ApiException.notFound("Рейс не найден");
        }
    }

    /**
     * Рейс в статусе «план», который сотрудник может менять.
     *
     * @param allowed есть ли у сотрудника право на изменение
     * @param message текст отказа, если права нет
     */
    private DbRec requirePlanEdit(long id, boolean allowed, String message) throws Exception {
        DbRec trip = requireTrip(id);
        if (!allowed) {
            throw ApiException.forbidden(message);
        }
        if (trip.getLong("trip_status_type") != TripStatus.PLANNED) {
            throw ApiException.conflict("Бригада меняется только у рейса в статусе «План»");
        }
        return trip;
    }

    /**
     * Рейс, начальника поезда и ПЭМ меняют планировщик вагонов и администратор.
     */
    private static boolean isWagonPlanner(CurrentUser user) {
        return user.hasRole(RoleType.WAGON_PLANNER) || user.hasRole(RoleType.ADMIN);
    }

    /**
     * Проводников рейса набирают и распределяют по вагонам планировщик проводников и администратор.
     */
    private static boolean isCrewPlanner(CurrentUser user) {
        return user.hasRole(RoleType.CREW_PLANNER) || user.hasRole(RoleType.ADMIN);
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

    /** Условие по равенству и is null: {key: value, nullKey: null}. */
    private static Map<String, Object> nullParam(String key, Object value, String nullKey) {
        Map<String, Object> res = new HashMap<>();
        res.put(key, value);
        res.put(nullKey, null);
        return res;
    }

}
