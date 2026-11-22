package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.kis.kisdbtools.repository.DbRecMapper;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.CrewDuty;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.TrainWagonChangeType;
import kz.zhsr.app.constants.TrainWagonKind;
import kz.zhsr.app.constants.TrainWagonPlanStatus;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.constants.WagonStatus;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.train.TrainCard;
import kz.zhsr.app.dto.train.TrainChange;
import kz.zhsr.app.dto.train.TrainFilter;
import kz.zhsr.app.dto.train.TrainOption;
import kz.zhsr.app.dto.train.TrainRow;
import kz.zhsr.app.dto.train.TrainRowWagon;
import kz.zhsr.app.dto.train.TrainState;
import kz.zhsr.app.dto.train.TrainTripMark;
import kz.zhsr.app.dto.train.TrainWagon;
import kz.zhsr.app.dto.train.WagonBusy;
import kz.zhsr.app.dto.trip.TripCrew;
import kz.zhsr.app.dto.trip.TripFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
 * Поезд (Ф-ПОЕ-00, Ф-ПОЕ-01, Ф-ПОЕ-02) и прицепка и отцепка вагонов: фактом и планом, вне рейса и в рейсе (Ф-РЕЙ-02,
 * Ф-АКТ-01). Состав поезда - техника «журнал и состояние» (doc/db-doc/journal-state-plan-fact.md): журнал прицепок и
 * отцепок train_wagon_change хранит события плана и факта, участие вагонов train_wagon - историю состава по факту и
 * ожидаемый состав; строит участие {@link TrainJournal}.
 * <p>
 * Прицепка и отцепка - одна операция над составом поезда, откуда бы её ни вызвали (состав поезда, формирование рейса,
 * прицепка в рейсе): рейс операции сервер определяет сам - по поезду и выбранной остановке маршрута. Прицепка ставит
 * вагон на позицию: вагоны с неё сдвигаются назад; отцепка сдвигает вагоны за отцепленным вперёд. Факт вносится
 * временем не раньше последнего факта поезда и вагона; удалить можно только факт со временем последнего факта поезда.
 * Поезд в рейсе в статусе «выполняется» меняется фактом только на остановке маршрута этого рейса: вагон выходит из
 * рейса на станцию остановки (или входит в рейс), вносится участок пробега; ответственных выбирает пользователь.
 * <p>
 * Права: поезд создают и меняют планировщик вагонов и администратор; прицепку и отцепку в рейсе в статусе
 * «выполняется» фактом оформляют ещё начальник поезда рейса и мастер депо станции маршрута. План рейса отмечает
 * начальник поезда рейса.
 */
@Service
public class TrainService {

    /** Формат времени в сообщениях. */
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    /** Состояние вагона рабочего парка (park_type.tsv: WORKING). */
    private static final Set<Long> WORKING = Set.of(WagonStatus.IN_IDLE, WagonStatus.IN_TRIP, WagonStatus.RESERVE);

    private static final List<Long> ACTIVE = List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS);

    /**
     * Обозначение поезда.
     */
    public record TrainBody(String name) {
    }

    /**
     * Прицепка или отцепка вагонов.
     *
     * @param wagons          номера вагонов; прицепляются подряд с позиции seq
     * @param fact            true - факт (уже совершилась), false - план
     * @param time            факт: фактическое время, null - сейчас; план: плановое время, у плана в рейсе null - время
     *                        стоянки на остановке по расписанию
     * @param station         вне рейса: станция; на остановке рейса не нужна - станция остановки
     * @param routeStation    остановка маршрута рейса поезда (route_station); рейс сервер находит сам: факт - рейс в
     *                        статусе «выполняется» (или ближайший в статусе «план» - на станции отправления), план -
     *                        рейс, у которого стоянка на этой остановке ближе всего ко времени time; null - вне рейса
     * @param seq             прицепка: позиция первого вагона, с 1; null или больше числа вагонов - в конец
     * @param comment         основания, примечания; может быть пусто
     * @param responsibleUsrs ответственные за вагоны - у факта в рейсе в статусе «выполняется»; пусто - не назначены
     */
    public record ChangeBody(List<String> wagons, boolean fact, OffsetDateTime time, Long station,
                             Long routeStation, Integer seq, String comment, List<Long> responsibleUsrs) {
    }

    /**
     * Отметка исполнения плана прицепки или отцепки.
     *
     * @param time            фактическое время; null - сейчас
     * @param station         фактическая станция плана вне рейса; null - станция плана
     * @param seq             прицепка: фактическая позиция; null - позиция плана
     * @param comment         основания, примечания факта; null - как у плана
     * @param responsibleUsrs ответственные за вагон - у отметки в рейсе в статусе «выполняется»; пусто - не назначены
     */
    public record MarkBody(OffsetDateTime time, Long station, Integer seq, String comment,
                           List<Long> responsibleUsrs) {
    }

    /**
     * Где совершается прицепка или отцепка.
     *
     * @param trip         рейс; null - вне рейса
     * @param inProgress   рейс в статусе «выполняется»: факт меняет журналы вагона
     * @param routeStation остановка маршрута рейса; null - вне рейса
     * @param station      станция; 0 - не задана
     * @param stopIndex    номер остановки среди остановок маршрута, с 0; вне рейса -1
     * @param stopCount    число остановок маршрута; вне рейса 0
     * @param stopTime     время стоянки на остановке по расписанию рейса; вне рейса null
     */
    private record Place(DbRec trip, boolean inProgress, Long routeStation, long station, int stopIndex, int stopCount,
                         OffsetDateTime stopTime) {
    }

    private final Db db;
    private final CurrentUserService currentUserService;
    private final TrainJournal trainJournal;
    private final WagonJournal wagonJournal;
    private final WagonRun wagonRun;
    private final ResponsibleService responsibleService;
    private final TripResponsibleService tripResponsibleService;
    private final RefResolver refResolver;
    private final Visibility visibility;

    public TrainService(Db db, CurrentUserService currentUserService, TrainJournal trainJournal,
                        WagonJournal wagonJournal, WagonRun wagonRun, ResponsibleService responsibleService,
                        TripResponsibleService tripResponsibleService, RefResolver refResolver,
                        Visibility visibility) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.trainJournal = trainJournal;
        this.wagonJournal = wagonJournal;
        this.wagonRun = wagonRun;
        this.responsibleService = responsibleService;
        this.tripResponsibleService = tripResponsibleService;
        this.refResolver = refResolver;
        this.visibility = visibility;
    }

    // --- чтение

    /**
     * Список поездов (GET /trains, Ф-ПОЕ-00): по обозначению.
     */
    public List<TrainRow> list(TrainFilter filter) throws Exception {
        currentUserService.get();
        return rows(filter, TrainRow.class);
    }

    /**
     * Действующие поезда для выбора в рейсе (GET /trains/options): у каждого - рейс, в котором поезд занят в
     * промежутке [from, to); рейс filter.trip не учитывается.
     */
    public List<TrainOption> options(TrainFilter filter) throws Exception {
        currentUserService.get();
        List<TrainOption> res = rows(new TrainFilter(List.of("active"), filter.search(), null, null, null),
                TrainOption.class);
        if (res.isEmpty() || filter.from() == null || filter.to() == null) {
            return res;
        }
        Map<Long, List<DbRec>> trips = trips(res.stream().map(TrainRow::getId).toList(), ACTIVE);
        for (TrainOption t : res) {
            for (DbRec trip : trips.getOrDefault(t.getId(), List.of())) {
                if (filter.trip() != null && trip.getLong("id").longValue() == filter.trip()) {
                    continue;
                }
                if (trip.getDateTimeTZ("beg").isBefore(filter.to()) && trip.getDateTimeTZ("fin").isAfter(filter.from())) {
                    t.setBusyTrip(new TripRef(trip.getLong("id")));
                    break;
                }
            }
        }
        refResolver.resolve(res.stream().map(TrainOption::getBusyTrip).filter(Objects::nonNull).toList());
        return res;
    }

    /**
     * Поезд с журналом и участием вагонов (GET /trains/{id}, Ф-ПОЕ-02): записи Факта и ожидающие записи Плана журнала,
     * вся история участия обоих слоёв, вагоны с текущим расположением, рейсы.
     */
    public TrainCard card(long id) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec train = requireTrain(id);
        TrainCard card = DbRecMapper.toObject(train, TrainCard.class);
        fill(List.of(card));
        card.setChanges(changes(journal(id), me));
        List<TrainState> states = new ArrayList<>(db.loadList("train_wagon", Map.of("train", id), TrainState.class));
        states.sort(Comparator.comparingInt(TrainState::getKind).thenComparing(TrainState::getDbeg)
                .thenComparingInt(TrainState::getSeq));
        card.setStates(states);
        Set<Long> wagons = new LinkedHashSet<>();
        states.forEach(s -> wagons.add(s.getWagon()));
        card.getChanges().forEach(c -> wagons.add(c.getWagon().getId()));
        card.setTrainWagons(wagons(List.copyOf(wagons), TrainWagon.class));
        card.setLastFactDt(trainJournal.lastFact(id));
        List<TrainTripMark> marks = new ArrayList<>();
        for (DbRec r : db.loadSql(TrainSql.tripMarks(), Map.of("train", id,
                "states", List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS, TripStatus.DONE)))) {
            TrainTripMark mark = DbRecMapper.toObject(r, TrainTripMark.class);
            mark.setTrip(new TripRef(r.getLong("id")));
            marks.add(mark);
        }
        card.setTripMarks(marks);
        card.setCanEdit(isPlanner(me) && train.get("dend") == null);
        // прицеплять и отцеплять: планировщик вагонов (к расформированному поезду - прицеплять, это снова открывает
        // поезд); поезд в рейсе - ещё начальник поезда рейса и мастер депо маршрута
        boolean couple = isPlanner(me);
        if (!couple && train.get("dend") == null) {
            for (DbRec trip : db.loadList("trip", Map.of("train", id, "trip_status_type", TripStatus.IN_PROGRESS))) {
                couple = isChief(trip.getLong("id"), me) || isMaster(trip, me);
            }
        }
        card.setCanCouple(couple);
        refResolver.resolve(card);
        refResolver.resolve(card.getTripMarks());
        return card;
    }

    /**
     * Вагоны поезда рейса на время рейса: до отправления - ожидаемый состав на плановое отправление (не раньше
     * последнего факта поезда) и вагоны, которые прицепят на остановках; в пути - факт с отправления и ожидаемые
     * прицепки до прибытия; после прибытия - факт рейса. Едет ли вагон (riding): до отправления - в составе на
     * отправлении, в пути - сейчас в этом рейсе, после - в составе на прибытии. По позиции: сначала едущие.
     *
     * @param changes прицепки и отцепки рейса (tripChanges): у каждого вагона - свои
     */
    List<TripCrew.Wagon> tripWagons(TripCrew.Trip trip, List<TrainChange> changes) throws Exception {
        if (trip.getTrain() == null) {
            return new ArrayList<>();
        }
        long train = trip.getTrain().getId();
        long status = trip.getTripStatusType();
        OffsetDateTime now = OffsetDateTime.now();
        List<DbRec> recs = new ArrayList<>();
        OffsetDateTime moment;
        if (status == TripStatus.IN_PROGRESS) {
            moment = now;
            recs.addAll(between(train, TrainWagonKind.FACT, trip.getDepartedDt(), now.plusNanos(1000)));
            if (trip.getArrival().isAfter(now)) {
                recs.addAll(between(train, TrainWagonKind.PLAN, now, trip.getArrival()));
            }
        } else if (status == TripStatus.DONE) {
            moment = trip.getArrivedDt();
            recs.addAll(between(train, TrainWagonKind.FACT, trip.getDepartedDt(), moment.plusNanos(1000)));
        } else {
            OffsetDateTime last = trainJournal.lastFact(train);
            moment = last != null && last.isAfter(trip.getPlannedDt()) ? last : trip.getPlannedDt();
            OffsetDateTime to = trip.getArrival().isAfter(moment) ? trip.getArrival() : moment.plusNanos(1000);
            recs.addAll(between(train, TrainWagonKind.PLAN, moment, to));
        }
        // одна строка на вагон: запись на момент moment, иначе первая в промежутке
        Map<Long, DbRec> byWagon = new LinkedHashMap<>();
        Set<Long> atMoment = new HashSet<>();
        for (DbRec r : recs) {
            long wagon = r.getLong("wagon");
            boolean covers = !r.getDateTimeTZ("dbeg").isAfter(moment)
                    && (r.get("dend") == null || r.getDateTimeTZ("dend").isAfter(moment));
            if (covers && atMoment.add(wagon)) {
                byWagon.put(wagon, r);
            } else {
                byWagon.putIfAbsent(wagon, r);
            }
        }
        Map<Long, TripCrew.Wagon> info = new HashMap<>();
        for (TripCrew.Wagon w : wagons(List.copyOf(byWagon.keySet()), TripCrew.Wagon.class)) {
            info.put(w.getWagon(), w);
        }
        List<TripCrew.Wagon> res = new ArrayList<>();
        for (Map.Entry<Long, DbRec> e : byWagon.entrySet()) {
            TripCrew.Wagon w = info.get(e.getKey());
            w.setSeq(e.getValue().getInt("seq"));
            w.setRiding(status == TripStatus.IN_PROGRESS
                    ? w.getLocationType() == LocationType.TRIP && w.getTrip() != null
                    && w.getTrip().getId() == trip.getId()
                    : atMoment.contains(e.getKey()));
            w.setChanges(changes.stream().filter(c -> c.getWagon().getId() == e.getKey()).toList());
            res.add(w);
        }
        Map<Long, OffsetDateTime> starts = new HashMap<>();
        byWagon.forEach((wagon, r) -> starts.put(wagon, r.getDateTimeTZ("dbeg")));
        res.sort(Comparator.comparing((TripCrew.Wagon w) -> !w.isRiding())
                .thenComparing(w -> w.isRiding() ? OffsetDateTime.MIN : starts.get(w.getWagon()))
                .thenComparingInt(TripCrew.Wagon::getSeq));
        return res;
    }

    private List<DbRec> between(long train, int kind, OffsetDateTime from, OffsetDateTime to) throws Exception {
        return db.loadSql(TrainSql.statesBetween(), Map.of("train", train, "kind", kind, "from", from, "to", to));
    }

    /**
     * Прицепки и отцепки рейса: записи Факта и ожидающие записи Плана, по времени.
     */
    List<TrainChange> tripChanges(long trip) throws Exception {
        List<DbRec> recs = new ArrayList<>(db.loadList("train_wagon_change", Map.of("trip", trip,
                "kind", TrainWagonKind.FACT)));
        recs.addAll(db.loadList("train_wagon_change", Map.of("trip", trip, "kind", TrainWagonKind.PLAN,
                "plan_status", TrainWagonPlanStatus.WAITING)));
        recs.sort(Comparator.comparing((DbRec r) -> r.getDateTimeTZ("dt")).thenComparing(r -> r.getLong("id")));
        return changes(recs, currentUserService.get());
    }

    /**
     * Планы рейса со статусом status, по времени: отменённые (их можно вернуть, restorePlan) или отработанные.
     *
     * @param status статус плана - TrainWagonPlanStatus
     */
    List<TrainChange> tripPlans(long trip, int status) throws Exception {
        List<DbRec> recs = new ArrayList<>(db.loadList("train_wagon_change", Map.of("trip", trip, "kind",
                TrainWagonKind.PLAN, "plan_status", status)));
        recs.sort(Comparator.comparing((DbRec r) -> r.getDateTimeTZ("dt")).thenComparing(r -> r.getLong("id")));
        CurrentUser me = currentUserService.get();
        List<TrainChange> res = changes(recs, me);
        if (status == TrainWagonPlanStatus.DONE && !res.isEmpty()) {
            // отработанный план: факт, который его отработал; отменить подтверждение - удалить этот факт (undo)
            List<Long> ids = res.stream().map(TrainChange::getId).toList();
            List<DbRec> factRecs = db.loadList("train_wagon_change", Map.of("plan", ids, "kind", TrainWagonKind.FACT));
            Map<Long, TrainChange> byPlan = new HashMap<>();
            for (TrainChange f : changes(factRecs, me)) {
                byPlan.put(f.getPlan(), f);
            }
            for (TrainChange c : res) {
                TrainChange f = byPlan.get(c.getId());
                if (f == null) {
                    continue;
                }
                c.setFact(f.getId());
                // права на удаление факта; последний ли он у поезда - видно по lastFactDt рейса
                c.setCanUndo(f.isCanUndo());
            }
        }
        return res;
    }

    /**
     * Перенести ожидающие планы прицепок и отцепок рейса trip вслед за его расписанием (после правки планового
     * отправления рейса, TripService.update): время каждого плана сдвигается на shift - на сколько сдвинулось плановое
     * отправление; ожидаемый состав поезда перестраивается. Вызывается внутри транзакции.
     */
    void shiftTripPlans(long trip, long train, Duration shift) throws Exception {
        List<DbRec> plans = db.loadList("train_wagon_change", Map.of("trip", trip, "kind", TrainWagonKind.PLAN,
                "plan_status", TrainWagonPlanStatus.WAITING));
        if (plans.isEmpty()) {
            return;
        }
        for (DbRec p : plans) {
            db.updateRec("train_wagon_change", Map.of("id", p.getLong("id"), "dt", p.getDateTimeTZ("dt").plus(shift)));
        }
        trainJournal.rebuildPlan(train);
        evictTrainCrew(train);
    }

    /**
     * Отменить ожидающие планы прицепок и отцепок рейса trip у поезда train (после смены поезда или маршрута рейса,
     * TripService.update: планы ссылаются на рейс прежнего поезда или на остановки прежнего маршрута) - как отмена
     * плана (cancelPlan): ожидаемый состав перестраивается, вагоны отменённых прицепок освобождаются от планов
     * ответственных, поезд без вагонов и планов расформировывается. Вызывается внутри транзакции.
     */
    void cancelTripPlans(long trip, long train) throws Exception {
        List<DbRec> plans = db.loadList("train_wagon_change", Map.of("trip", trip, "train", train,
                "kind", TrainWagonKind.PLAN, "plan_status", TrainWagonPlanStatus.WAITING));
        if (plans.isEmpty()) {
            return;
        }
        for (DbRec p : plans) {
            db.updateRec("train_wagon_change", Map.of("id", p.getLong("id"),
                    "plan_status", TrainWagonPlanStatus.CANCELLED));
        }
        trainJournal.rebuildPlan(train);
        boolean attach = false;
        for (DbRec p : plans) {
            if (p.getInt("change_type") == TrainWagonChangeType.ATTACH) {
                tripResponsibleService.releasePlanned(train, p.getLong("wagon"), null);
                attach = true;
            }
        }
        if (attach) {
            dissolveIfEmpty(train, OffsetDateTime.now());
        }
        evictTrainCrew(train);
    }

    /**
     * Ожидающие планы поезда, которые должны быть отмечены или отменены до отправления рейса: рейса на станции
     * отправления и вне рейса не позже планового отправления.
     */
    List<TrainChange> pendingBefore(long train, long trip, long departureRouteStation, OffsetDateTime departure)
            throws Exception {
        List<DbRec> recs = new ArrayList<>();
        for (DbRec c : waiting(train)) {
            Long changeTrip = c.getLong("trip", null);
            boolean atDeparture = changeTrip != null && changeTrip == trip
                    && c.getLong("route_station").longValue() == departureRouteStation;
            boolean before = changeTrip == null && !c.getDateTimeTZ("dt").isAfter(departure);
            if (atDeparture || before) {
                recs.add(c);
            }
        }
        return changes(recs, currentUserService.get());
    }

    /**
     * Занятость вагонов в поездах с времени from (GET /trains/busy): ожидаемый состав поездов (в нём и вагоны поезда
     * по факту), который не закончился к from; у каждого - рейс поезда в это время.
     */
    public List<WagonBusy> busy(List<String> numbers, OffsetDateTime from) throws Exception {
        currentUserService.get();
        List<Long> wagons = new ArrayList<>();
        for (String number : numbers == null ? List.<String>of() : numbers) {
            DbRec w = db.loadRec("wagon", Map.of("number", number));
            if (w != null) {
                wagons.add(w.getLong("id"));
            }
        }
        return busyOf(wagons, from == null ? OffsetDateTime.now() : from);
    }

    private List<WagonBusy> busyOf(List<Long> wagons, OffsetDateTime from) throws Exception {
        if (wagons.isEmpty()) {
            return new ArrayList<>();
        }
        List<DbRec> rows = db.loadSql(TrainSql.busy(), Map.of("wagons", wagons, "from", from,
                "plan", TrainWagonKind.PLAN));
        Map<Long, List<DbRec>> trips = rows.isEmpty() ? Map.of()
                : trips(rows.stream().map(r -> r.getLong("train")).distinct().toList(),
                List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS, TripStatus.DONE));
        List<WagonBusy> res = new ArrayList<>();
        for (DbRec r : rows) {
            WagonBusy b = new WagonBusy();
            b.setNumber(r.getString("number"));
            b.setTrain(new TrainRef(r.getLong("train")));
            b.setDbeg(r.getDateTimeTZ("beg"));
            b.setDend(r.get("fin") == null ? null : r.getDateTimeTZ("fin"));
            OffsetDateTime start = b.getDbeg().isAfter(from) ? b.getDbeg() : from;
            for (DbRec trip : trips.getOrDefault(r.getLong("train"), List.of())) {
                if (trip.getDateTimeTZ("fin").isAfter(start)
                        && (b.getDend() == null || trip.getDateTimeTZ("beg").isBefore(b.getDend()))) {
                    b.setTrip(new TripRef(trip.getLong("id")));
                    break;
                }
            }
            res.add(b);
        }
        refResolver.resolve(res);
        return res;
    }

    // --- поезд

    /**
     * Удалить поезд (DELETE /trains/{id}): планировщик вагонов и администратор. В поезде есть или были вагоны, у него
     * есть рейсы - удалить не даст внешний ключ (сообщение - FkMessages).
     */
    @Transactional
    public void delete(long id) throws Exception {
        requirePlanner(currentUserService.get());
        requireTrain(id);
        try {
            db.deleteRec("train", id);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    /**
     * Создать поезд без вагонов (POST /trains, Ф-ПОЕ-01).
     */
    @Transactional
    public TrainRow create(TrainBody body) throws Exception {
        requirePlanner(currentUserService.get());
        String name = name(body);
        long id;
        try {
            id = db.insertRec("train", Map.of("name", name, "dbeg", OffsetDateTime.now()));
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        return row(id);
    }

    /**
     * Исправить обозначение действующего поезда (PUT /trains/{id}, Ф-ПОЕ-01).
     */
    @Transactional
    public TrainRow rename(long id, TrainBody body) throws Exception {
        requirePlanner(currentUserService.get());
        requireActive(id);
        try {
            db.updateRec("train", Map.of("id", id, "name", name(body)));
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        return row(id);
    }

    // --- прицепка и отцепка

    /**
     * Прицепить вагоны к поезду фактом или планом (POST /trains/{id}/attach): подряд с позиции body.seq. Прицепка к
     * расформированному поезду снова открывает его; обозначение не должно повторять обозначение действующего поезда.
     */
    @Transactional
    public TrainCard attach(long id, ChangeBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        reopen(id);
        Place place = placeFor(id, body);
        requireRight(me, place, body.fact());
        List<String> numbers = numbers(body);
        OffsetDateTime time = time(body, place);
        if (place.trip() == null && place.station() == 0) {
            throw ApiException.badRequest("Укажите станцию прицепки");
        }
        if (body.fact() && place.inProgress()) {
            responsibleService.check(body.responsibleUsrs(), place.station(), place.trip().getLong("id"));
        }
        try {
            int kind = body.fact() ? TrainWagonKind.FACT : TrainWagonKind.PLAN;
            int seq = position(id, kind, time, body.seq());
            for (int i = 0; i < numbers.size(); i++) {
                DbRec wagon = requireWagon(numbers.get(i));
                if (body.fact()) {
                    insertFact(id, wagon, TrainWagonChangeType.ATTACH, time, seq + i, place, null, body.comment(),
                            body.responsibleUsrs(), me);
                } else {
                    requireWorking(wagon);
                    requireNotIn(id, wagon, TrainWagonKind.PLAN, time);
                    requireFree(wagon, time, id);
                    db.insertRec("train_wagon_change", changeValues(id, wagon.getLong("id"),
                            TrainWagonChangeType.ATTACH, TrainWagonKind.PLAN, time, seq + i, place, null, me,
                            body.comment()));
                }
            }
            if (body.fact()) {
                trainJournal.applyFact(id, time);
            }
            trainJournal.rebuildPlan(id);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        evictTrainCrew(id);
        return card(id);
    }

    /**
     * Отцепить вагоны от поезда фактом или планом (POST /trains/{id}/detach).
     */
    @Transactional
    public TrainCard detach(long id, ChangeBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        requireActive(id);
        Place place = placeFor(id, body);
        requireRight(me, place, body.fact());
        List<String> numbers = numbers(body);
        OffsetDateTime time = time(body, place);
        if (body.fact() && place.inProgress()) {
            responsibleService.check(body.responsibleUsrs(), place.station(), place.trip().getLong("id"));
        }
        try {
            for (String number : numbers) {
                DbRec wagon = requireWagon(number);
                long wagonId = wagon.getLong("id");
                if (body.fact()) {
                    Place p = place.trip() == null ? stationOf(wagon) : place;
                    insertFact(id, wagon, TrainWagonChangeType.DETACH, time, null, p, null, body.comment(),
                            body.responsibleUsrs(), me);
                } else {
                    if (trainJournal.at(id, TrainWagonKind.PLAN, time).stream()
                            .noneMatch(r -> r.getLong("wagon").longValue() == wagonId)) {
                        throw ApiException.conflict("На момент " + format(time) + " вагона " + number
                                + " в поезде по плану уже нет");
                    }
                    if (db.loadRec("train_wagon_change", Map.of("train", id, "wagon", wagonId, "kind",
                            TrainWagonKind.PLAN, "plan_status", TrainWagonPlanStatus.WAITING,
                            "change_type", TrainWagonChangeType.DETACH)) != null) {
                        throw ApiException.conflict("У вагона " + number + " уже запланирована отцепка");
                    }
                    Place p = place.trip() == null && place.station() == 0 ? stationOf(wagon) : place;
                    db.insertRec("train_wagon_change", changeValues(id, wagonId, TrainWagonChangeType.DETACH,
                            TrainWagonKind.PLAN, time, null, p, null, me, body.comment()));
                    tripResponsibleService.releasePlanned(id, wagonId, time);
                }
            }
            if (body.fact()) {
                trainJournal.applyFact(id, time);
            }
            trainJournal.rebuildPlan(id);
            if (body.fact()) {
                dissolveIfEmpty(id, time);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        evictTrainCrew(id);
        return card(id);
    }

    /**
     * Отметить исполнение плана прицепки или отцепки (POST /train-changes/{id}/done): факт по плану, план -
     * «отработан». Время, станцию плана вне рейса и позицию прицепки можно исправить.
     */
    @Transactional
    public TrainCard mark(long changeId, MarkBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec plan = requireChange(changeId);
        if (plan.getInt("kind") != TrainWagonKind.PLAN
                || plan.getInt("plan_status") != TrainWagonPlanStatus.WAITING) {
            throw ApiException.conflict("Отмечается только ожидающий план прицепки или отцепки");
        }
        long train = plan.getLong("train");
        requireActive(train);
        Place place = place(train, plan.getLong("trip", null), plan.getLong("route_station", null),
                plan.getLong("station"));
        if (!canMark(me, place)) {
            throw ApiException.forbidden(place.trip() == null
                    ? "Прицепку и отцепку вне рейса отмечает планировщик вагонов"
                    : "Прицепку и отцепку рейса отмечает начальник поезда рейса");
        }
        if (place.trip() != null && !place.inProgress() && place.stopIndex() != 0) {
            throw ApiException.conflict("Прицепка и отцепка на остановке в пути отмечаются после отправления рейса");
        }
        OffsetDateTime time = place.inProgress() ? ActTime.check(body.time()) : notFuture(body.time());
        if (place.trip() == null && body.station() != null) {
            place = place(train, null, null, body.station());
        }
        if (place.inProgress()) {
            responsibleService.check(body.responsibleUsrs(), place.station(), place.trip().getLong("id"));
        }
        DbRec wagon = db.loadRec("wagon", plan.getLong("wagon"));
        boolean attach = plan.getInt("change_type") == TrainWagonChangeType.ATTACH;
        try {
            Integer seq = attach ? (body.seq() != null ? body.seq() : Integer.valueOf(plan.getInt("seq"))) : null;
            insertFact(train, wagon, plan.getInt("change_type"), time, seq, place, changeId,
                    body.comment() != null ? body.comment() : plan.get("comment") == null ? null
                            : plan.getString("comment"), body.responsibleUsrs(), me);
            db.updateRec("train_wagon_change", Map.of("id", changeId, "plan_status", TrainWagonPlanStatus.DONE));
            trainJournal.applyFact(train, time);
            trainJournal.rebuildPlan(train);
            if (!attach) {
                dissolveIfEmpty(train, time);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        evictTrainCrew(train);
        return card(train);
    }

    /**
     * Исправить запись журнала (PUT /train-changes/{id}). Ожидающий план - целиком: плановое время, место (станция или
     * остановка рейса), позиция прицепки, основания; проверки - как у нового плана на новое время. Факт - только
     * основания и примечания: время и место факта меняют журналы вагона, ошибочный факт удаляется и вносится заново.
     * Права: план - как у отмены плана (и на новое место - как у нового плана), факт - как у удаления факта.
     *
     * @param body у плана - время, станция или остановка, позиция и основания (wagons и fact не используются); у факта -
     *             только comment
     */
    @Transactional
    public TrainCard edit(long changeId, ChangeBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec change = requireChange(changeId);
        long train = change.getLong("train");
        Map<Long, DbRec> tripsById = new HashMap<>();
        Long tripId = change.getLong("trip", null);
        Place old = tripId == null ? new Place(null, false, null, change.getLong("station"), -1, 0, null)
                : placeOf(tripId, change.getLong("route_station", null), tripsById);
        String comment = body.comment() == null || body.comment().isBlank() ? null : body.comment().trim();
        if (change.getInt("kind") == TrainWagonKind.FACT) {
            if (!canUndo(me, old)) {
                throw ApiException.forbidden("Основания факта исправляет тот, кто может удалить факт");
            }
            Map<String, Object> values = new HashMap<>();
            values.put("id", changeId);
            values.put("comment", comment);
            try {
                db.updateRec("train_wagon_change", values);
            } catch (Exception e) {
                throw DbErrors.translate(e);
            }
            return card(train);
        }
        if (change.getInt("plan_status") != TrainWagonPlanStatus.WAITING) {
            throw ApiException.conflict("Исправляется только ожидающий план прицепки или отцепки");
        }
        if (!canCancelPlan(me, old)) {
            throw ApiException.forbidden("План исправляет планировщик вагонов или начальник поезда рейса");
        }
        ChangeBody plan = new ChangeBody(List.of(), false, body.time(), body.station(), body.routeStation(),
                body.seq(), comment, List.of());
        Place place = placeFor(train, plan);
        requireRight(me, place, false);
        OffsetDateTime time = time(plan, place);
        DbRec wagon = db.loadRec("wagon", change.getLong("wagon"));
        long wagonId = wagon.getLong("id");
        boolean attach = change.getInt("change_type") == TrainWagonChangeType.ATTACH;
        try {
            // проверки - по ожидаемому составу без исправляемого плана: он на время проверок отменён
            db.updateRec("train_wagon_change", Map.of("id", changeId, "plan_status", TrainWagonPlanStatus.CANCELLED));
            trainJournal.rebuildPlan(train);
            Integer seq = null;
            Place where = place;
            if (attach) {
                if (place.trip() == null && place.station() == 0) {
                    throw ApiException.badRequest("Укажите станцию прицепки");
                }
                requireWorking(wagon);
                requireNotIn(train, wagon, TrainWagonKind.PLAN, time);
                requireFree(wagon, time, train);
                seq = position(train, TrainWagonKind.PLAN, time, body.seq());
            } else {
                if (trainJournal.at(train, TrainWagonKind.PLAN, time).stream()
                        .noneMatch(r -> r.getLong("wagon").longValue() == wagonId)) {
                    throw ApiException.conflict("На момент " + format(time) + " вагона " + wagon.getString("number")
                            + " в поезде по плану уже нет");
                }
                if (place.trip() == null && place.station() == 0) {
                    where = stationOf(wagon);
                }
            }
            Map<String, Object> values = new HashMap<>();
            values.put("id", changeId);
            values.put("plan_status", TrainWagonPlanStatus.WAITING);
            values.put("dt", time);
            values.put("seq", seq);
            values.put("station", where.station());
            values.put("trip", where.trip() == null ? null : where.trip().getLong("id"));
            values.put("route_station", where.routeStation());
            values.put("comment", comment);
            db.updateRec("train_wagon_change", values);
            if (!attach) {
                tripResponsibleService.releasePlanned(train, wagonId, time);
            }
            trainJournal.rebuildPlan(train);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        evictTrainCrew(train);
        return card(train);
    }

    /**
     * Отменить план прицепки или отцепки (POST /train-changes/{id}/cancel): статус «отменён», ожидаемый состав
     * перестраивается.
     */
    @Transactional
    public TrainCard cancelPlan(long changeId) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec plan = requireChange(changeId);
        if (plan.getInt("kind") != TrainWagonKind.PLAN
                || plan.getInt("plan_status") != TrainWagonPlanStatus.WAITING) {
            throw ApiException.conflict("Отменяется только ожидающий план прицепки или отцепки");
        }
        long train = plan.getLong("train");
        Place place = place(train, plan.getLong("trip", null), plan.getLong("route_station", null),
                plan.getLong("station"));
        if (!canCancelPlan(me, place)) {
            throw ApiException.forbidden("План отменяет планировщик вагонов или начальник поезда рейса");
        }
        try {
            db.updateRec("train_wagon_change", Map.of("id", changeId, "plan_status", TrainWagonPlanStatus.CANCELLED));
            trainJournal.rebuildPlan(train);
            if (plan.getInt("change_type") == TrainWagonChangeType.ATTACH) {
                tripResponsibleService.releasePlanned(train, plan.getLong("wagon"), null);
                dissolveIfEmpty(train, OffsetDateTime.now());
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        evictTrainCrew(train);
        return card(train);
    }

    /**
     * Вернуть отменённый план прицепки или отцепки (POST /train-changes/{id}/restore): статус снова «ожидает», ожидаемый
     * состав перестраивается. Проверки - как у нового плана на его время: прицепка - вагон рабочего парка, не в поезде и
     * свободен (расформированный поезд снова открывается); отцепка - вагон в ожидаемом составе, другой отцепки у вагона
     * не запланировано. План с прошедшим плановым временем не возвращается: он стал бы ожидающим задним числом (раньше
     * уже отправленных рейсов) - нужен новый план. Права - как у отмены плана.
     */
    @Transactional
    public TrainCard restorePlan(long changeId) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec plan = requireChange(changeId);
        if (plan.getInt("kind") != TrainWagonKind.PLAN
                || plan.getInt("plan_status") != TrainWagonPlanStatus.CANCELLED) {
            throw ApiException.conflict("Возвращается только отменённый план прицепки или отцепки");
        }
        long train = plan.getLong("train");
        Place place = place(train, plan.getLong("trip", null), plan.getLong("route_station", null),
                plan.getLong("station"));
        if (!canCancelPlan(me, place)) {
            throw ApiException.forbidden("План возвращает планировщик вагонов или начальник поезда рейса");
        }
        if (!plan.getDateTimeTZ("dt").isAfter(OffsetDateTime.now())) {
            throw ApiException.conflict("Плановое время прошло: внесите новый план");
        }
        DbRec wagon = db.loadRec("wagon", plan.getLong("wagon"));
        long wagonId = wagon.getLong("id");
        // план раньше последнего факта действует с последнего факта
        OffsetDateTime last = trainJournal.lastFact(train);
        OffsetDateTime time = plan.getDateTimeTZ("dt");
        if (last != null && time.isBefore(last)) {
            time = last;
        }
        try {
            if (plan.getInt("change_type") == TrainWagonChangeType.ATTACH) {
                reopen(train);
                requireWorking(wagon);
                requireNotIn(train, wagon, TrainWagonKind.PLAN, time);
                requireFree(wagon, time, train);
            } else {
                requireActive(train);
                if (trainJournal.at(train, TrainWagonKind.PLAN, time).stream()
                        .noneMatch(r -> r.getLong("wagon").longValue() == wagonId)) {
                    throw ApiException.conflict("На момент " + format(time) + " вагона " + wagon.getString("number")
                            + " в поезде по плану уже нет");
                }
                if (db.loadRec("train_wagon_change", Map.of("train", train, "wagon", wagonId, "kind",
                        TrainWagonKind.PLAN, "plan_status", TrainWagonPlanStatus.WAITING,
                        "change_type", TrainWagonChangeType.DETACH)) != null) {
                    throw ApiException.conflict("У вагона " + wagon.getString("number")
                            + " уже запланирована отцепка");
                }
            }
            db.updateRec("train_wagon_change", Map.of("id", changeId, "plan_status", TrainWagonPlanStatus.WAITING));
            trainJournal.rebuildPlan(train);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        evictTrainCrew(train);
        return card(train);
    }

    /**
     * Удалить ошибочный факт прицепки или отцепки без истории (DELETE /train-changes/{id}): факт, после которого вагон
     * больше не прицепляли и не отцепляли (ни в каком поезде); прицепки и отцепки других вагонов не мешают. Записи
     * журналов вагона, которые открыл факт, удаляются, прежние снова текущие (они должны быть ещё текущими); участие
     * поезда с этого времени строится заново по оставшимся фактам; отработанный фактом план - снова «ожидает».
     */
    @Transactional
    public TrainCard undo(long changeId) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec change = requireChange(changeId);
        if (change.getInt("kind") != TrainWagonKind.FACT) {
            throw ApiException.conflict("Удаляется только факт прицепки или отцепки");
        }
        long train = change.getLong("train");
        long wagon = change.getLong("wagon");
        Long trip = change.getLong("trip", null);
        Place place = place(train, trip, change.getLong("route_station", null), change.getLong("station"));
        if (!canUndo(me, place)) {
            throw ApiException.forbidden(trip == null
                    ? "Прицепку и отцепку вне рейса отменяет планировщик вагонов"
                    : "Прицепку и отцепку рейса отменяет начальник поезда рейса");
        }
        OffsetDateTime dt = change.getDateTimeTZ("dt");
        // более поздние факты этого же вагона - в этом поезде или в другом (там вагон оказался бы в двух поездах сразу)
        for (DbRec r : db.loadList("train_wagon_change", Map.of("wagon", wagon, "kind", TrainWagonKind.FACT))) {
            OffsetDateTime rdt = r.getDateTimeTZ("dt");
            boolean after = rdt.isAfter(dt) || (rdt.isEqual(dt) && r.getLong("id") > changeId);
            if (r.getLong("train").longValue() != train && !rdt.isBefore(dt)) {
                throw ApiException.conflict("После этого вагон прицепляли или отцепляли в другом поезде - сначала "
                        + "отмените это");
            }
            if (r.getLong("train").longValue() == train && after) {
                throw ApiException.conflict("После этого вагон " + db.loadRec("wagon", wagon).getString("number")
                        + " прицепляли или отцепляли - сначала отмените это");
            }
        }
        WagonJournal.Opened opened = opened(change);
        boolean attach = change.getInt("change_type") == TrainWagonChangeType.ATTACH;
        try {
            if (opened == null) {
                requireNoTripSince(train, dt);
            }
            if (!attach && trip != null && opened != null) {
                Long attachChange = trainJournal.attachOf(train, wagon, dt);
                if (attachChange != null) {
                    wagonRun.removeSegment(trip, attachChange);
                }
            }
            if (attach) {
                tripResponsibleService.releasePlanned(train, wagon, null);
                if (trip != null && opened != null) {
                    tripResponsibleService.releaseAfter(trip, wagon);
                }
            }
            // запись журнала ссылается на записи журналов вагона: сначала она, затем они
            db.deleteRec("train_wagon_change", changeId);
            if (opened != null) {
                wagonJournal.undo(opened);
            }
            Long plan = change.getLong("plan", null);
            if (plan != null) {
                db.updateRec("train_wagon_change", Map.of("id", plan, "plan_status", TrainWagonPlanStatus.WAITING));
            }
            trainJournal.applyFact(train, dt);
            trainJournal.rebuildPlan(train);
            Map<String, Object> reopen = new HashMap<>();
            reopen.put("id", train);
            reopen.put("dend", null);
            db.updateRec("train", reopen);
            dissolveIfEmpty(train, OffsetDateTime.now());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        evictTrainCrew(train);
        return card(train);
    }

    // --- внутреннее: прицепка и отцепка

    /**
     * Внести запись Факта прицепки или отцепки вагона: проверки, журналы вагона в рейсе в статусе «выполняется»,
     * участок пробега отцепки в рейсе, снятие планов ответственных. Участие поезда применяет вызывающий
     * ({@link TrainJournal#applyFact}).
     *
     * @param seq  прицепка: позиция
     * @param plan отработанный план; null - факт без плана: тогда факт отрабатывает ожидающий план того же вида у
     *             вагона того же рейса или с временем не позже факта, если он есть
     */
    private void insertFact(long train, DbRec wagon, int type, OffsetDateTime time, Integer seq, Place place, Long plan,
                            String comment, List<Long> responsibles, CurrentUser me) throws Exception {
        long wagonId = wagon.getLong("id");
        String number = wagon.getString("number");
        requireFactTime(train, wagonId, time);
        boolean attach = type == TrainWagonChangeType.ATTACH;
        if (attach) {
            requireWorking(wagon);
            requireNotIn(train, wagon, TrainWagonKind.FACT, time);
            requireFree(wagon, time, train);
            requireAtStation(train, wagon, place);
        } else {
            if (inTrain(train, wagonId) == null) {
                throw ApiException.conflict("На момент " + format(time) + " вагона " + number
                        + " в поезде уже нет");
            }
            if (!place.inProgress() && isInProgressTrip(train)) {
                throw ApiException.conflict("Поезд в рейсе: отцепка оформляется в рейсе");
            }
        }
        // факт без плана отрабатывает ожидающий план того же вида у вагона - того же рейса или уже наступивший (не
        // позже факта): план не остаётся просроченным; будущий план другого рейса или вне рейса остаётся ожидающим
        if (plan == null) {
            DbRec waiting = db.loadRec("train_wagon_change", Map.of("train", train, "wagon", wagonId, "kind",
                    TrainWagonKind.PLAN, "plan_status", TrainWagonPlanStatus.WAITING, "change_type", type));
            Long planTrip = waiting == null ? null : waiting.getLong("trip", null);
            boolean sameTrip = planTrip != null && place.trip() != null
                    && planTrip.longValue() == place.trip().getLong("id");
            if (waiting != null && (sameTrip || !waiting.getDateTimeTZ("dt").isAfter(time))) {
                plan = waiting.getLong("id");
                db.updateRec("train_wagon_change", Map.of("id", plan, "plan_status", TrainWagonPlanStatus.DONE));
            }
        }
        Map<String, Object> values = changeValues(train, wagonId, type, TrainWagonKind.FACT, time, seq, place, plan, me,
                comment);
        if (place.inProgress()) {
            long trip = place.trip().getLong("id");
            if (attach) {
                putOpened(values, wagonJournal.move(wagonId, time, LocationType.TRIP, null, trip, WagonStatus.IN_TRIP,
                        responsibles));
            } else {
                DbRec info = db.loadRec("wagon_info", Map.of("wagon", wagonId));
                Long infoTrip = info.getLong("trip", null);
                if (info.getLong("location_type") != LocationType.TRIP || infoTrip == null || infoTrip != trip) {
                    throw ApiException.conflict("Вагон " + number + " не едет в поезде рейса");
                }
                putOpened(values, wagonJournal.move(wagonId, time, LocationType.STATION, place.station(), null,
                        WagonStatus.IN_IDLE, responsibles));
            }
        }
        db.insertRec("train_wagon_change", values);
        if (!attach) {
            if (place.inProgress()) {
                long trip = place.trip().getLong("id");
                Long attachChange = trainJournal.attachOf(train, wagonId, time);
                if (attachChange != null) {
                    wagonRun.addSegment(trip, attachChange, place.routeStation());
                }
                tripResponsibleService.releaseAfter(trip, wagonId);
            }
            tripResponsibleService.releasePlanned(train, wagonId, null);
        }
    }

    /**
     * Позиция прицепки: заданная (не больше числа вагонов плюс 1) или в конец - по составу слоя kind на время time.
     */
    private int position(long train, int kind, OffsetDateTime time, Integer seq) throws Exception {
        int size = kind == TrainWagonKind.FACT
                ? db.loadSql(TrainSql.openStates(), Map.of("train", train, "kind", kind)).size()
                : trainJournal.at(train, kind, time).size();
        if (seq == null || seq > size + 1) {
            return size + 1;
        }
        if (seq < 1) {
            throw ApiException.badRequest("Позиция в поезде - с 1");
        }
        return seq;
    }

    /** Открытая запись Факта участия вагона в поезде; вагон не в поезде - null. */
    private DbRec inTrain(long train, long wagon) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("train", train);
        params.put("wagon", wagon);
        params.put("kind", TrainWagonKind.FACT);
        params.put("dend", null);
        return db.loadRec("train_wagon", params);
    }

    /** Вагон ещё не в поезде: по факту сейчас или в ожидаемом составе на время time. */
    private void requireNotIn(long train, DbRec wagon, int kind, OffsetDateTime time) throws Exception {
        long wagonId = wagon.getLong("id");
        boolean in = kind == TrainWagonKind.FACT ? inTrain(train, wagonId) != null
                : trainJournal.at(train, kind, time).stream().anyMatch(r -> r.getLong("wagon").longValue() == wagonId);
        if (in) {
            throw ApiException.conflict("Вагон " + wagon.getString("number") + " уже в поезде");
        }
    }

    /** Время факта - не раньше последнего факта поезда и вагона. */
    private void requireFactTime(long train, long wagon, OffsetDateTime time) throws Exception {
        OffsetDateTime last = trainJournal.lastFact(train);
        if (last != null && time.isBefore(last)) {
            throw ApiException.badRequest("Время должно быть не раньше последней прицепки или отцепки поезда: "
                    + format(last));
        }
        OffsetDateTime wagonLast = trainJournal.lastWagonFact(wagon);
        if (wagonLast != null && time.isBefore(wagonLast)) {
            throw ApiException.badRequest("Время должно быть не раньше последней прицепки или отцепки вагона: "
                    + format(wagonLast));
        }
    }

    /**
     * Место новой прицепки или отцепки по выбору пользователя: остановка маршрута - рейс поезда находится сам (см.
     * ChangeBody.routeStation); остановки нет - вне рейса, на станции station.
     */
    private Place placeFor(long train, ChangeBody body) throws Exception {
        if (body.routeStation() == null) {
            if (body.fact() && isInProgressTrip(train)) {
                throw ApiException.badRequest("Поезд в рейсе: выберите станцию маршрута рейса");
            }
            return place(train, null, null, body.station());
        }
        DbRec rs = db.loadRec("route_station", body.routeStation());
        if (rs == null || !rs.getBoolean("have_stop")) {
            throw ApiException.badRequest("Выберите остановку маршрута рейса");
        }
        List<DbRec> trips = db.loadList("trip", Map.of("train", train, "route", rs.getLong("route"))).stream()
                .filter(t -> ACTIVE.contains(t.getLong("trip_status_type")))
                .sorted(Comparator.comparing(t -> t.getDateTimeTZ("planned_dt")))
                .toList();
        if (trips.isEmpty()) {
            throw ApiException.badRequest("У поезда нет рейса по маршруту этой остановки");
        }
        if (body.fact()) {
            DbRec trip = trips.stream().filter(t -> t.getLong("trip_status_type") == TripStatus.IN_PROGRESS)
                    .findFirst().orElse(trips.get(0));
            return place(train, trip.getLong("id"), body.routeStation(), body.station());
        }
        Place best = null;
        for (DbRec trip : trips) {
            Place p = place(train, trip.getLong("id"), body.routeStation(), body.station());
            if (body.time() == null) {
                return p;
            }
            if (best == null || Math.abs(java.time.Duration.between(p.stopTime(), body.time()).toMinutes())
                    < Math.abs(java.time.Duration.between(best.stopTime(), body.time()).toMinutes())) {
                best = p;
            }
        }
        return best;
    }

    /**
     * Место прицепки или отцепки: рейс и остановка или станция вне рейса.
     */
    private Place place(long train, Long tripId, Long routeStation, Long station) throws Exception {
        if (tripId == null) {
            return new Place(null, false, null, station == null ? 0 : station, -1, 0, null);
        }
        DbRec trip = db.loadSqlRec(TrainSql.trip(), Map.of("id", tripId));
        if (trip == null) {
            throw ApiException.notFound("Рейс не найден");
        }
        Long tripTrain = trip.getLong("train", null);
        if (tripTrain == null || tripTrain != train) {
            throw ApiException.badRequest("Поезд не назначен на этот рейс");
        }
        long status = trip.getLong("trip_status_type");
        if (status != TripStatus.PLANNED && status != TripStatus.IN_PROGRESS) {
            throw ApiException.conflict("Рейс закончен или отменён");
        }
        if (routeStation == null) {
            throw ApiException.badRequest("Выберите остановку маршрута");
        }
        DbRec rs = db.loadRec("route_station", routeStation);
        if (rs == null || rs.getLong("route").longValue() != trip.getLong("route") || !rs.getBoolean("have_stop")) {
            throw ApiException.badRequest("Выберите остановку маршрута рейса");
        }
        List<DbRec> stops = db.loadList("route_station", Map.of("route", trip.getLong("route"), "have_stop", true));
        stops = stops.stream().sorted(Comparator.comparingInt(a -> a.getInt("seq"))).toList();
        int index = 0;
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getLong("id").longValue() == routeStation) {
                index = i;
            }
        }
        OffsetDateTime start = trip.getDateTimeTZ("planned_dt");
        Integer minutes = rs.get("departure_min") != null ? Integer.valueOf(rs.getInt("departure_min"))
                : rs.get("arrival_min") != null ? Integer.valueOf(rs.getInt("arrival_min")) : null;
        DbRec first = stops.isEmpty() ? null : stops.get(0);
        int firstMin = first == null || first.get("departure_min") == null ? 0 : first.getInt("departure_min");
        OffsetDateTime stopTime = minutes == null ? start : start.plusMinutes(minutes - firstMin);
        return new Place(trip, status == TripStatus.IN_PROGRESS, routeStation, rs.getLong("station"), index,
                stops.size(), stopTime);
    }

    /** Место отцепки вне рейса: станция расположения вагона. */
    private Place stationOf(DbRec wagon) throws Exception {
        DbRec rec = db.loadSqlRec(stationSql(), Map.of("wagon", wagon.getLong("id")));
        Long station = rec == null ? null : rec.getLong("station", null);
        if (station == null) {
            throw ApiException.conflict("Вагон " + wagon.getString("number") + " в рейсе: отцепка оформляется в рейсе");
        }
        return new Place(null, false, null, station, -1, 0, null);
    }

    private static String stationSql() {
        return """
                select %s as station
                from wagon_info
                where wagon_info.wagon = :wagon
                """.formatted(TrainSql.WAGON_STATION);
    }

    /**
     * Время изменения: факт - не в будущем (в рейсе в статусе «выполняется» - не раньше суток назад), сейчас по
     * умолчанию; план - в будущем: указанное, а в рейсе по умолчанию - время стоянки на остановке.
     */
    private OffsetDateTime time(ChangeBody body, Place place) {
        if (body.fact()) {
            if (place.trip() != null && !place.inProgress() && place.stopIndex() != 0) {
                throw ApiException.badRequest("До отправления фактом прицепляют и отцепляют только на станции отправления");
            }
            if (place.inProgress() && (place.stopIndex() <= 0 || place.stopIndex() >= place.stopCount() - 1)) {
                throw ApiException.badRequest("Выберите промежуточную остановку маршрута");
            }
            return place.inProgress() ? ActTime.check(body.time()) : notFuture(body.time());
        }
        if (place.trip() != null && place.stopIndex() >= place.stopCount() - 1) {
            throw ApiException.badRequest("На станции прибытия прицепка и отцепка не планируются");
        }
        if (place.inProgress() && place.stopIndex() <= 0) {
            throw ApiException.badRequest("Рейс отправлен: выберите остановку в пути");
        }
        OffsetDateTime time = body.time() != null ? body.time() : place.stopTime();
        if (time == null) {
            throw ApiException.badRequest("Укажите плановое время");
        }
        if (!time.isAfter(OffsetDateTime.now())) {
            throw ApiException.badRequest("Плановое время должно быть в будущем");
        }
        return time;
    }

    private static OffsetDateTime notFuture(OffsetDateTime time) {
        OffsetDateTime now = OffsetDateTime.now();
        if (time == null) {
            return now;
        }
        if (time.isAfter(now.plusMinutes(1))) {
            throw ApiException.badRequest("Время не может быть в будущем");
        }
        return time.isAfter(now) ? now : time;
    }

    /**
     * Фактом прицепляется вагон на станции прицепки: в рейсе - на самой станции; вне рейса - на станции, в её депо или
     * парке отстоя, и только к поезду, вагоны которого стоят там же (или у поезда вагонов нет).
     */
    private void requireAtStation(long train, DbRec wagon, Place place) throws Exception {
        DbRec info = db.loadRec("wagon_info", Map.of("wagon", wagon.getLong("id")));
        String number = wagon.getString("number");
        if (place.inProgress()) {
            Long station = info.getLong("station", null);
            if (info.getLong("location_type") != LocationType.STATION || station == null
                    || station != place.station()) {
                throw ApiException.conflict("Вагон " + number + " должен стоять на станции прицепки; вагон в депо или "
                        + "парке отстоя сначала передаётся на станцию");
            }
            return;
        }
        if (isInProgressTrip(train)) {
            throw ApiException.conflict("Поезд в рейсе: прицепка оформляется в рейсе");
        }
        if (place.station() == 0) {
            throw ApiException.badRequest("Укажите станцию");
        }
        DbRec at = db.loadSqlRec(stationSql(), Map.of("wagon", wagon.getLong("id")));
        Long station = at == null ? null : at.getLong("station", null);
        if (station == null || station != place.station()) {
            throw ApiException.conflict("Вагон " + number + " не на станции прицепки: его можно прицепить только планом");
        }
        List<Long> inTrain = db.loadSql(TrainSql.openStates(), Map.of("train", train, "kind", TrainWagonKind.FACT))
                .stream().map(r -> r.getLong("wagon")).toList();
        for (TrainWagon w : wagons(inTrain, TrainWagon.class)) {
            if (w.getStation() == null || w.getStation() != place.station()) {
                throw ApiException.conflict("Вагоны поезда не на станции прицепки: вагон " + number
                        + " можно прицепить только планом");
            }
        }
    }

    /** Вагон рабочего парка, в эксплуатации. */
    private void requireWorking(DbRec wagon) throws Exception {
        if (wagon.get("dend") != null) {
            throw ApiException.conflict("Вагон " + wagon.getString("number") + " выведен из эксплуатации");
        }
        DbRec info = db.loadRec("wagon_info", Map.of("wagon", wagon.getLong("id")));
        if (!WORKING.contains(info.getLong("wagon_status_type"))) {
            throw ApiException.conflict("Вагон " + wagon.getString("number") + " не в рабочем парке");
        }
    }

    /**
     * Вагон не занят в другом поезде с времени from: ни по факту, ни в ожидаемом составе - плановая прицепка
     * резервирует вагон.
     *
     * @param train поезд, к которому прицепляют: его занятость не учитывается
     */
    private void requireFree(DbRec wagon, OffsetDateTime from, long train) throws Exception {
        List<WagonBusy> busy = busyOf(List.of(wagon.getLong("id")), from).stream()
                .filter(b -> b.getTrain().getId() != train).toList();
        if (busy.isEmpty()) {
            return;
        }
        WagonBusy b = busy.get(0);
        StringBuilder msg = new StringBuilder("Вагон " + wagon.getString("number") + " занят: поезд "
                + b.getTrain().getName());
        if (b.getTrip() != null) {
            msg.append(", рейс ").append(b.getTrip().getTrainNumber()).append(' ').append(b.getTrip().getName());
        }
        msg.append(", с ").append(format(b.getDbeg()));
        if (b.getDend() != null) {
            msg.append(" по ").append(format(b.getDend()));
        }
        throw ApiException.conflict(msg.toString());
    }

    private static String format(OffsetDateTime time) {
        return time.atZoneSameInstant(ZoneId.systemDefault()).format(TIME);
    }

    /** Поезд не ходил в рейс после времени since: иначе изменение до рейса уже не отменить. */
    private void requireNoTripSince(long train, OffsetDateTime since) throws Exception {
        for (DbRec trip : db.loadList("trip", Map.of("train", train))) {
            OffsetDateTime departed = trip.getDateTimeTZ("departed_dt");
            if (departed != null && departed.isAfter(since)) {
                throw ApiException.conflict("После этого поезд уже ходил в рейс - отменить нельзя");
            }
        }
    }

    /** Поезд расформировывается, если в нём нет вагонов по факту и ожидающих плановых прицепок. */
    private void dissolveIfEmpty(long train, OffsetDateTime time) throws Exception {
        DbRec alive = db.loadSqlRec(TrainSql.alive(), Map.of("train", train, "fact", TrainWagonKind.FACT,
                "plan", TrainWagonKind.PLAN, "waiting", TrainWagonPlanStatus.WAITING,
                "attach", TrainWagonChangeType.ATTACH));
        if (alive.getLong("cnt") == 0) {
            db.updateRec("train", Map.of("id", train, "dend", time));
        }
    }

    private boolean isInProgressTrip(long train) throws Exception {
        return !db.loadList("trip", Map.of("train", train, "trip_status_type", TripStatus.IN_PROGRESS)).isEmpty();
    }

    private static Map<String, Object> changeValues(long train, long wagon, int type, int kind, OffsetDateTime time,
                                                    Integer seq, Place place, Long plan, CurrentUser me,
                                                    String comment) {
        Map<String, Object> values = new HashMap<>();
        values.put("train", train);
        values.put("wagon", wagon);
        values.put("kind", kind);
        values.put("plan_status", kind == TrainWagonKind.PLAN ? Integer.valueOf(TrainWagonPlanStatus.WAITING) : null);
        values.put("change_type", type);
        values.put("dt", time);
        values.put("seq", seq);
        values.put("station", place.station());
        values.put("trip", place.trip() == null ? null : place.trip().getLong("id"));
        values.put("route_station", place.routeStation());
        values.put("plan", plan);
        values.put("usr", me.id());
        values.put("comment", comment == null || comment.isBlank() ? null : comment.trim());
        return values;
    }

    private static void putOpened(Map<String, Object> values, WagonJournal.Opened opened) {
        values.put("wagon_location", opened.location());
        values.put("wagon_status", opened.status());
        values.put("wagon_responsible", opened.responsibles().opened());
        values.put("closed_wagon_responsible", opened.responsibles().closed());
    }

    /** Записи журналов вагона, открытые фактом; не открывал - null. */
    private static WagonJournal.Opened opened(DbRec change) {
        Long location = change.getLong("wagon_location", null);
        Long status = change.getLong("wagon_status", null);
        return location == null || status == null ? null
                : new WagonJournal.Opened(location, status, new WagonJournal.Responsibles(
                        change.getLong("wagon_responsible", null), change.getLong("closed_wagon_responsible", null)));
    }

    private static List<String> numbers(ChangeBody body) {
        List<String> numbers = body.wagons() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(body.wagons()));
        if (numbers.isEmpty()) {
            throw ApiException.badRequest("Отметьте вагоны");
        }
        return numbers;
    }

    // --- права

    private static boolean isPlanner(CurrentUser me) {
        return me.hasRole(RoleType.WAGON_PLANNER) || me.hasRole(RoleType.ADMIN);
    }

    private static void requirePlanner(CurrentUser me) {
        if (!isPlanner(me)) {
            throw ApiException.forbidden("Поезд меняет планировщик вагонов");
        }
    }

    private boolean isChief(long trip, CurrentUser me) throws Exception {
        return db.loadRec("trip_crew", Map.of("trip", trip, "usr", me.id(), "crew_duty_type", CrewDuty.LNP)) != null;
    }

    /** Мастер депо станции маршрута рейса. */
    private boolean isMaster(DbRec trip, CurrentUser me) throws Exception {
        if (!me.hasRole(RoleType.MASTER) || me.depotStation() == null) {
            return false;
        }
        return db.loadRec("route_station", Map.of("route", trip.getLong("route"), "station", me.depotStation(),
                "have_stop", true)) != null;
    }

    /**
     * Право на прицепку или отцепку: планировщик вагонов и администратор; факт в рейсе в статусе «выполняется» - ещё
     * начальник поезда рейса и мастер депо станции маршрута.
     */
    private void requireRight(CurrentUser me, Place place, boolean fact) throws Exception {
        if (me.hasRole(RoleType.ADMIN)) {
            return;
        }
        if (fact && place.inProgress()) {
            if (!isPlanner(me) && !isChief(place.trip().getLong("id"), me) && !isMaster(place.trip(), me)) {
                throw ApiException.forbidden("Прицепку и отцепку в рейсе оформляет планировщик вагонов, начальник "
                        + "поезда рейса или мастер депо станции маршрута");
            }
            return;
        }
        if (place.inProgress() && isChief(place.trip().getLong("id"), me)) {
            return;
        }
        requirePlanner(me);
    }

    /** Может ли сотрудник отметить план: план вне рейса - планировщик; план рейса - начальник поезда рейса, в пути -
     * ещё мастер депо станции маршрута. */
    private boolean canMark(CurrentUser me, Place place) throws Exception {
        if (me.hasRole(RoleType.ADMIN)) {
            return true;
        }
        if (place.trip() == null) {
            return isPlanner(me);
        }
        return isChief(place.trip().getLong("id"), me) || (place.inProgress() && isMaster(place.trip(), me));
    }

    /** Может ли сотрудник отменить план: кто может отметить его, и планировщик вагонов - и в рейсе. */
    private boolean canCancelPlan(CurrentUser me, Place place) throws Exception {
        return canMark(me, place) || isPlanner(me);
    }

    /**
     * Может ли сотрудник удалить факт прицепки или отцепки (train_wagon_change.id): журнал вагона.
     */
    boolean canUndo(CurrentUser me, long changeId) throws Exception {
        DbRec change = db.loadRec("train_wagon_change", changeId);
        if (change == null || change.getInt("kind") != TrainWagonKind.FACT) {
            return false;
        }
        Long trip = change.getLong("trip", null);
        Place place = trip == null ? new Place(null, false, null, change.getLong("station"), -1, 0, null)
                : placeOf(trip, change.getLong("route_station", null), new HashMap<>());
        return canUndo(me, place);
    }

    /** Может ли сотрудник удалить факт: так же, как отметить; факт рейса до отправления - ещё планировщик. */
    private boolean canUndo(CurrentUser me, Place place) throws Exception {
        if (place.trip() != null && !place.inProgress() && isPlanner(me)) {
            return true;
        }
        return canMark(me, place);
    }

    // --- общее

    /** Журнал поезда: записи Факта и ожидающие записи Плана, по времени. */
    private List<DbRec> journal(long train) throws Exception {
        List<DbRec> recs = new ArrayList<>(db.loadList("train_wagon_change", Map.of("train", train,
                "kind", TrainWagonKind.FACT)));
        recs.addAll(waiting(train));
        recs.sort(Comparator.comparing((DbRec r) -> r.getDateTimeTZ("dt")).thenComparing(r -> r.getLong("id")));
        return recs;
    }

    /** Ожидающие записи Плана поезда, по времени. */
    private List<DbRec> waiting(long train) throws Exception {
        List<DbRec> recs = new ArrayList<>(db.loadList("train_wagon_change", Map.of("train", train,
                "kind", TrainWagonKind.PLAN, "plan_status", TrainWagonPlanStatus.WAITING)));
        recs.sort(Comparator.comparing((DbRec r) -> r.getDateTimeTZ("dt")).thenComparing(r -> r.getLong("id")));
        return recs;
    }

    /**
     * Записи журнала для показа: время и станция отработанного плана - вторым проходом, просрочка плана, права
     * текущего сотрудника.
     */
    private List<TrainChange> changes(List<DbRec> recs, CurrentUser me) throws Exception {
        List<TrainChange> res = DbRecMapper.toList(recs, TrainChange.class);
        List<Long> plans = res.stream().map(TrainChange::getPlan).filter(Objects::nonNull).toList();
        Map<Long, DbRec> planById = plans.isEmpty() ? Map.of()
                : UtDb.collectGroupBy_record(db.loadList("train_wagon_change", Map.of("id", plans)), "id");
        OffsetDateTime now = OffsetDateTime.now();
        Map<Long, DbRec> tripsById = new HashMap<>();
        for (int i = 0; i < res.size(); i++) {
            TrainChange c = res.get(i);
            DbRec rec = recs.get(i);
            DbRec plan = c.getPlan() == null ? null : planById.get(c.getPlan());
            if (plan != null) {
                c.setPlanDt(plan.getDateTimeTZ("dt"));
                c.setPlanStation(plan.getLong("station"));
            }
            boolean waiting = c.getKind() == TrainWagonKind.PLAN && c.getPlanStatus() != null
                    && c.getPlanStatus() == TrainWagonPlanStatus.WAITING;
            c.setOverdue(waiting && c.getDt().isBefore(now));
            Long trip = rec.getLong("trip", null);
            Place place = trip == null ? new Place(null, false, null, c.getStation(), -1, 0, null)
                    : placeOf(trip, c.getRouteStation(), tripsById);
            c.setCanMark(waiting && canMark(me, place));
            c.setCanCancel(waiting && canCancelPlan(me, place));
            c.setCanUndo(c.getKind() == TrainWagonKind.FACT && canUndo(me, place));
            // исправить: ожидающий план - кто может его отменить, факт (только основания) - кто может его удалить
            c.setCanEdit(waiting ? c.isCanCancel() : c.isCanUndo());
            boolean cancelled = c.getKind() == TrainWagonKind.PLAN && c.getPlanStatus() != null
                    && c.getPlanStatus() == TrainWagonPlanStatus.CANCELLED;
            // вернуть можно только план с плановым временем в будущем
            c.setCanRestore(cancelled && c.getDt().isAfter(now) && canCancelPlan(me, place));
        }
        refResolver.resolve(res);
        return res;
    }

    private Place placeOf(long trip, Long routeStation, Map<Long, DbRec> tripsById) throws Exception {
        DbRec t = tripsById.get(trip);
        if (t == null) {
            t = db.loadSqlRec(TrainSql.trip(), Map.of("id", trip));
            tripsById.put(trip, t);
        }
        return new Place(t, t.getLong("trip_status_type") == TripStatus.IN_PROGRESS, routeStation, 0, 0, 0, null);
    }

    /** Вагоны с текущими расположением и состоянием; пассажирский - по типу вагона. */
    private <T extends TrainWagon> List<T> wagons(List<Long> wagons, Class<T> clazz) throws Exception {
        if (wagons.isEmpty()) {
            return new ArrayList<>();
        }
        List<DbRec> recs = db.loadSql(TrainSql.wagons(), Map.of("wagons", wagons));
        List<T> res = new ArrayList<>();
        for (DbRec r : recs) {
            T w = DbRecMapper.toObject(r, clazz);
            w.setPassenger(!TripService.NOT_PASSENGER.contains(r.getString("code")));
            res.add(w);
        }
        addResponsibles(res);
        refResolver.resolve(res);
        return res;
    }

    /** Текущие ответственные за вагоны - по текущей записи журнала ответственных вагона. */
    private void addResponsibles(List<? extends TrainWagon> list) throws Exception {
        Map<Long, TrainWagon> byRecord = new HashMap<>();
        for (TrainWagon w : list) {
            if (w.getWagonResponsible() != null) {
                byRecord.put(w.getWagonResponsible(), w);
            }
        }
        if (byRecord.isEmpty()) {
            return;
        }
        for (DbRec rec : db.loadSql(WagonSql.responsibleUsrs(), Map.of("records", List.copyOf(byRecord.keySet())))) {
            byRecord.get(rec.getLong("wagon_responsible")).getResponsibleUsrs().add(new UserRef(rec.getLong("usr")));
        }
        refResolver.resolve(list.stream().flatMap(w -> w.getResponsibleUsrs().stream()).toList());
    }

    private <T extends TrainRow> List<T> rows(TrainFilter filter, Class<T> clazz) throws Exception {
        SqlWhere where = new SqlWhere();
        List<String> states = filter.states() == null ? List.of() : filter.states();
        if (states.contains("active") != states.contains("closed")) {
            where.add(states.contains("active") ? "train.dend is null" : "train.dend is not null");
        }
        String search = filter.search() == null ? "" : filter.search().trim();
        if (!search.isEmpty()) {
            where.add("(strpos(lower(train.name), lower(:search)) > 0 or train.id in (" + TrainSql.byWagonNumber()
                    + "))", "search", search);
            where.param("fact", TrainWagonKind.FACT);
        }
        SqlQuery q = where.query(TrainSql.trains(), "order by train.name, train.id");
        List<T> res = db.loadSql(q.sql(), q.params(), clazz);
        fill(res);
        return res;
    }

    /** Вычисляемые поля строк поездов: вагоны сейчас по факту, их место, число ожидающих планов, рейсы. */
    private void fill(List<? extends TrainRow> rows) throws Exception {
        if (rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(TrainRow::getId).toList();
        Map<Long, List<DbRec>> wagons = UtDb.collectGroupBy_records(db.loadSql(TrainSql.openWagons(),
                Map.of("trains", ids, "kind", TrainWagonKind.FACT)), "train");
        Map<Long, DbRec> counts = UtDb.collectGroupBy_record(db.loadSql(TrainSql.waitingCounts(),
                Map.of("trains", ids, "plan", TrainWagonKind.PLAN, "waiting", TrainWagonPlanStatus.WAITING)), "train");
        Map<Long, List<DbRec>> trips = trips(ids, ACTIVE);
        CurrentUser me = currentUserService.get();
        for (TrainRow row : rows) {
            List<TrainRowWagon> refs = new ArrayList<>();
            Set<Long> stations = new HashSet<>();
            // вид и место расположения вагонов: «вид:место»; одно на всех - вагоны в одном месте
            Set<String> places = new HashSet<>();
            Long locationType = null;
            Long place = null;
            boolean away = false;
            for (DbRec w : wagons.getOrDefault(row.getId(), List.of())) {
                TrainRowWagon ref = new TrainRowWagon();
                ref.setId(w.getLong("wagon"));
                ref.setNumber(w.getString("number"));
                ref.setWagonType(w.getLong("wagon_type"));
                ref.setModelType(w.getLong("model_type", null));
                refs.add(ref);
                locationType = w.getLong("location_type", null);
                place = w.getLong("place", null);
                places.add(locationType + ":" + place);
                Long station = w.getLong("station", null);
                if (station == null) {
                    away = true;
                } else {
                    stations.add(station);
                }
            }
            row.setWagons(refs);
            if (places.size() == 1) {
                row.setLocationType(locationType);
                row.setPlace(place);
            }
            row.setStation(!away && stations.size() == 1 ? stations.iterator().next() : null);
            DbRec count = counts.get(row.getId());
            row.setPlannedCount(count == null ? 0 : count.getInt("cnt"));
            // рейсы поезда видны тому, кому виден поезд (Visibility.trainVisible), - для просмотра
            List<TripRef> rowTrips = new ArrayList<>();
            boolean trainVisible = visibility.trainVisible(me, row.getId());
            for (DbRec trip : trips.getOrDefault(row.getId(), List.of())) {
                if (!trainVisible) {
                    continue;
                }
                TripRef ref = new TripRef(trip.getLong("id"));
                rowTrips.add(ref);
                if (trip.getLong("trip_status_type") == TripStatus.IN_PROGRESS) {
                    row.setTrip(ref);
                } else if (row.getNextTrip() == null) {
                    row.setNextTrip(ref);
                }
            }
            row.setTrips(rowTrips);
        }
        refResolver.resolve(rows);
        refResolver.resolve(rows.stream().flatMap(r -> r.getTrips().stream()).toList());
    }

    private Map<Long, List<DbRec>> trips(List<Long> trains, List<Long> states) throws Exception {
        return UtDb.collectGroupBy_records(db.loadSql(TrainSql.trainTrips(), Map.of("trains", trains,
                "states", states)), "train");
    }

    private TrainRow row(long id) throws Exception {
        TrainRow row = DbRecMapper.toObject(requireTrain(id), TrainRow.class);
        fill(List.of(row));
        return row;
    }

    private static String name(TrainBody body) {
        if (body == null || body.name() == null || body.name().isBlank()) {
            throw ApiException.badRequest("Укажите обозначение поезда");
        }
        String name = body.name().trim();
        if (name.length() > 50) {
            throw ApiException.badRequest("Обозначение поезда - не длиннее 50 символов");
        }
        return name;
    }

    DbRec requireTrain(long id) throws Exception {
        DbRec train = db.loadRec("train", id);
        if (train == null) {
            throw ApiException.notFound("Поезд не найден");
        }
        return train;
    }

    /** Расформированный поезд снова открыть: прицепка возвращает его в действующие (в транзакции прицепки). */
    private void reopen(long id) throws Exception {
        DbRec train = requireTrain(id);
        if (train.get("dend") == null) {
            return;
        }
        Map<String, Object> same = new HashMap<>();
        same.put("name", train.getString("name"));
        same.put("dend", null);
        if (db.loadRec("train", same) != null) {
            throw ApiException.conflict("Поезд " + train.getString("name")
                    + " уже действует: расформированный поезд с этим обозначением снова не открыть");
        }
        Map<String, Object> values = new HashMap<>();
        values.put("id", id);
        values.put("dend", null);
        try {
            db.updateRec("train", values);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    private DbRec requireActive(long id) throws Exception {
        DbRec train = requireTrain(id);
        if (train.get("dend") != null) {
            throw ApiException.conflict("Поезд расформирован");
        }
        return train;
    }

    private DbRec requireChange(long id) throws Exception {
        DbRec change = db.loadRec("train_wagon_change", id);
        if (change == null) {
            throw ApiException.notFound("Прицепка или отцепка не найдена");
        }
        return change;
    }

    private DbRec requireWagon(String number) throws Exception {
        DbRec wagon = db.loadRec("wagon", Map.of("number", number));
        if (wagon == null) {
            throw ApiException.badRequest("Вагон " + number + " не найден");
        }
        return wagon;
    }

    /** Сбросить кэш сотрудников бригад рейсов поезда: их вагоны могли поменяться. */
    private void evictTrainCrew(long train) throws Exception {
        for (DbRec trip : db.loadList("trip", Map.of("train", train, "trip_status_type", ACTIVE))) {
            for (DbRec r : db.loadList("trip_crew", Map.of("trip", trip.getLong("id")))) {
                currentUserService.evict(r.getLong("usr"));
            }
        }
    }

}
