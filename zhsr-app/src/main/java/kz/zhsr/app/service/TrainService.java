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
import kz.zhsr.app.constants.TrainWagonChangeStatus;
import kz.zhsr.app.constants.TrainWagonChangeType;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.constants.WagonStatus;
import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.train.TrainCard;
import kz.zhsr.app.dto.train.TrainChange;
import kz.zhsr.app.dto.train.TrainFilter;
import kz.zhsr.app.dto.train.TrainOption;
import kz.zhsr.app.dto.train.TrainRow;
import kz.zhsr.app.dto.train.TrainRowWagon;
import kz.zhsr.app.dto.train.TrainWagon;
import kz.zhsr.app.dto.train.WagonBusy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Поезд (Ф-ПОЕ-00, Ф-ПОЕ-01, Ф-ПОЕ-02) и прицепка и отцепка вагонов: фактом и планом, вне рейса и в рейсе (Ф-РЕЙ-02,
 * Ф-АКТ-01).
 * <p>
 * Права: поезд создают и меняют вне рейса планировщик вагонов и администратор. Прицепку и отцепку в рейсе в статусе
 * «выполняется» фактом оформляют начальник поезда рейса, мастер депо станции маршрута и администратор; планируют -
 * ещё и планировщик вагонов. Плановую прицепку и отцепку рейса в статусе «план» на станции отправления отмечает
 * начальник поезда рейса.
 * <p>
 * Прицепка и отцепка рейса в статусе «выполняется» открывают записи журналов вагона и ссылаются на них; вне рейса
 * журналы не меняются. Ошибочный факт отменяется без истории.
 */
@Service
public class TrainService {

    /** Формат времени в сообщении о занятости вагона. */
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    /** Состояние вагона рабочего парка (park_type.tsv: WORKING). */
    private static final Set<Long> WORKING = Set.of(WagonStatus.IN_IDLE, WagonStatus.IN_TRIP, WagonStatus.RESERVE);

    private static final List<Long> ACTIVE = List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS);

    static final OffsetDateTime INFINITY = OffsetDateTime.MAX;

    /** Даль для запроса вместо INFINITY: OffsetDateTime.MAX в БД не передаётся. */
    private static final OffsetDateTime FAR = OffsetDateTime.parse("9999-12-31T00:00:00Z");

    /**
     * Обозначение поезда.
     */
    public record TrainBody(String name) {
    }

    /**
     * Прицепка или отцепка вагонов.
     *
     * @param wagons         номера вагонов
     * @param fact           true - факт (уже совершилась), false - план
     * @param time           факт: фактическое время, null - сейчас; план: плановое время, у плана в рейсе null -
     *                       время стоянки на остановке по расписанию
     * @param station        вне рейса: станция; в рейсе не нужна - станция остановки
     * @param trip           рейс поезда, если прицепка или отцепка для рейса; null - вне рейса
     * @param routeStation   остановка маршрута рейса (route_station); задаётся вместе с trip
     * @param comment        основания, примечания; может быть пусто
     * @param responsibleUsr ответственный за вагоны - у факта в рейсе в статусе «выполняется»; null - не назначен
     */
    public record ChangeBody(List<String> wagons, boolean fact, OffsetDateTime time, Long station, Long trip,
                             Long routeStation, String comment, Long responsibleUsr) {
    }

    /**
     * Отметка плановой прицепки или отцепки выполненной.
     *
     * @param time           фактическое время; null - сейчас
     * @param responsibleUsr ответственный за вагон - у отметки в рейсе в статусе «выполняется»; null - не назначен
     */
    public record MarkBody(OffsetDateTime time, Long responsibleUsr) {
    }

    /**
     * Где совершается прицепка или отцепка.
     *
     * @param trip         рейс; null - вне рейса
     * @param inProgress   рейс в статусе «выполняется»: факт меняет журналы вагона
     * @param routeStation остановка маршрута рейса; null - вне рейса
     * @param station      станция
     * @param stopIndex    номер остановки среди остановок маршрута, с 0; вне рейса -1
     * @param stopCount    число остановок маршрута; вне рейса 0
     * @param stopTime     время стоянки на остановке по расписанию рейса; вне рейса null
     */
    private record Place(DbRec trip, boolean inProgress, Long routeStation, long station, int stopIndex, int stopCount,
                         OffsetDateTime stopTime) {
    }

    private final Db db;
    private final CurrentUserService currentUserService;
    private final WagonJournal wagonJournal;
    private final WagonRun wagonRun;
    private final ResponsibleService responsibleService;
    private final CrewRows crewRows;
    private final RefResolver refResolver;

    public TrainService(Db db, CurrentUserService currentUserService, WagonJournal wagonJournal, WagonRun wagonRun,
                        ResponsibleService responsibleService, CrewRows crewRows, RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.wagonJournal = wagonJournal;
        this.wagonRun = wagonRun;
        this.responsibleService = responsibleService;
        this.crewRows = crewRows;
        this.refResolver = refResolver;
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
                if (filter.trip() != null && trip.getLong("id") == filter.trip()) {
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
     * Поезд с вагонами сейчас и с плановой прицепкой, рейсами и плановыми изменениями (GET /trains/{id}).
     */
    public TrainCard card(long id) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec train = requireTrain(id);
        TrainCard card = DbRecMapper.toObject(train, TrainCard.class);
        fill(List.of(card));
        OffsetDateTime now = OffsetDateTime.now();
        card.setTrainWagons(wagons(id, now, INFINITY, TrainWagon.class));
        List<TripRef> trips = new ArrayList<>();
        for (DbRec trip : trips(List.of(id), ACTIVE).getOrDefault(id, List.of())) {
            trips.add(new TripRef(trip.getLong("id")));
        }
        card.setTrips(trips);
        card.setCanEdit(isPlanner(me) && train.get("dend") == null);
        refResolver.resolve(card);
        refResolver.resolve(card.getTrips());
        return card;
    }

    /**
     * Вагоны поезда, участие которых действует в промежутке [from, to): по факту и по плану, по позиции в поезде, с
     * плановыми и выполненными прицепками и отцепками.
     */
    <T extends TrainWagon> List<T> wagons(long train, OffsetDateTime from, OffsetDateTime to, Class<T> clazz)
            throws Exception {
        CurrentUser me = currentUserService.get();
        Map<String, Object> params = new HashMap<>();
        params.put("trains", List.of(train));
        params.put("from", from);
        params.put("to", to == INFINITY ? FAR : to);
        List<DbRec> rows = db.loadSql(TrainSql.trainWagons(), params);
        List<T> res = new ArrayList<>();
        if (rows.isEmpty()) {
            return res;
        }
        Map<Long, List<DbRec>> changes = UtDb.collectGroupBy_records(db.loadList("train_wagon_change",
                Map.of("train_wagon", rows.stream().map(r -> r.getLong("id")).toList())), "train_wagon");
        Map<Long, DbRec> tripsById = new HashMap<>();
        for (DbRec r : rows) {
            T w = DbRecMapper.toObject(r, clazz);
            w.setPassenger(!TripService.NOT_PASSENGER.contains(r.getString("code")));
            for (DbRec c : changes.getOrDefault(r.getLong("id"), List.of())) {
                if (c.getInt("status") == TrainWagonChangeStatus.CANCELLED) {
                    continue;
                }
                TrainChange change = change(c, r.getString("number"), me, tripsById);
                boolean attach = c.getInt("change_type") == TrainWagonChangeType.ATTACH;
                boolean done = c.getInt("status") == TrainWagonChangeStatus.DONE;
                if (attach && done) {
                    w.setAttached(change);
                } else if (attach) {
                    w.setAttachPlan(change);
                } else if (done) {
                    w.setDetached(change);
                } else {
                    w.setDetachPlan(change);
                }
            }
            res.add(w);
        }
        refResolver.resolve(res);
        return res;
    }

    /**
     * Прицепки и отцепки рейса (статус «план» и «выполнено»), по времени.
     */
    List<TrainChange> tripChanges(long trip) throws Exception {
        CurrentUser me = currentUserService.get();
        List<TrainChange> res = new ArrayList<>();
        Map<Long, DbRec> tripsById = new HashMap<>();
        for (DbRec c : db.loadList("train_wagon_change", Map.of("trip", trip))) {
            if (c.getInt("status") == TrainWagonChangeStatus.CANCELLED) {
                continue;
            }
            DbRec row = db.loadRec("train_wagon", c.getLong("train_wagon"));
            String number = db.loadRec("wagon", row.getLong("wagon")).getString("number");
            res.add(change(c, number, me, tripsById));
        }
        res.sort((a, b) -> time(a).compareTo(time(b)));
        refResolver.resolve(res);
        return res;
    }

    private static OffsetDateTime time(TrainChange c) {
        return c.getFactDt() != null ? c.getFactDt() : c.getPlannedDt();
    }

    /**
     * Плановые прицепки и отцепки поезда, которые должны быть отмечены до отправления рейса: рейса на станции
     * отправления и вне рейса не позже планового отправления.
     */
    List<TrainChange> pendingBefore(long train, long trip, long departureRouteStation, OffsetDateTime departure)
            throws Exception {
        CurrentUser me = currentUserService.get();
        List<TrainChange> res = new ArrayList<>();
        Map<Long, DbRec> tripsById = new HashMap<>();
        List<DbRec> rows = db.loadList("train_wagon", Map.of("train", train));
        if (rows.isEmpty()) {
            return res;
        }
        Map<Long, DbRec> byId = UtDb.collectGroupBy_record(rows, "id");
        for (DbRec c : db.loadList("train_wagon_change", Map.of("train_wagon", List.copyOf(byId.keySet()),
                "status", TrainWagonChangeStatus.PLANNED))) {
            Long changeTrip = c.getLong("trip", null);
            boolean atDeparture = changeTrip != null && changeTrip == trip
                    && c.getLong("route_station") == departureRouteStation;
            boolean before = changeTrip == null && !c.getDateTimeTZ("planned_dt").isAfter(departure);
            if (atDeparture || before) {
                String number = db.loadRec("wagon", byId.get(c.getLong("train_wagon")).getLong("wagon"))
                        .getString("number");
                res.add(change(c, number, me, tripsById));
            }
        }
        refResolver.resolve(res);
        return res;
    }

    /**
     * Занятость вагонов в поездах с времени from (GET /trains/busy): участия по факту и по плану, которые не
     * закончились к from; у каждого - рейс поезда в это время.
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
        return busyOf(wagons, from == null ? OffsetDateTime.now() : from, null);
    }

    private List<WagonBusy> busyOf(List<Long> wagons, OffsetDateTime from, Long exceptTrainWagon) throws Exception {
        if (wagons.isEmpty()) {
            return new ArrayList<>();
        }
        List<DbRec> rows = db.loadSql(TrainSql.busy(), Map.of("wagons", wagons, "from", from));
        rows = rows.stream().filter(r -> exceptTrainWagon == null || r.getLong("id") != exceptTrainWagon).toList();
        Map<Long, List<DbRec>> trips = rows.isEmpty() ? Map.of()
                : trips(rows.stream().map(r -> r.getLong("train")).distinct().toList(),
                List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS, TripStatus.DONE));
        List<WagonBusy> res = new ArrayList<>();
        for (DbRec r : rows) {
            WagonBusy b = new WagonBusy();
            b.setNumber(r.getString("number"));
            b.setTrain(new TrainRef(r.getLong("train")));
            b.setDbeg(r.getDateTimeTZ("beg"));
            b.setDend(r.getDateTimeTZ("fin"));
            OffsetDateTime start = b.getDbeg().isAfter(from) ? b.getDbeg() : from;
            OffsetDateTime end = b.getDend() == null ? INFINITY : b.getDend();
            for (DbRec trip : trips.getOrDefault(r.getLong("train"), List.of())) {
                if (trip.getDateTimeTZ("fin").isAfter(start) && trip.getDateTimeTZ("beg").isBefore(end)) {
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

    /**
     * Порядок вагонов поезда (PUT /trains/{id}/order): номера по порядку; вагоны не из списка встают после них.
     */
    @Transactional
    public TrainCard order(long id, List<String> numbers) throws Exception {
        requirePlanner(currentUserService.get());
        requireActive(id);
        List<TrainWagon> wagons = wagons(id, OffsetDateTime.now(), INFINITY, TrainWagon.class);
        List<TrainWagon> ordered = new ArrayList<>();
        for (String number : numbers == null ? List.<String>of() : numbers) {
            wagons.stream().filter(w -> w.getNumber().equals(number) && !ordered.contains(w)).findFirst()
                    .ifPresent(ordered::add);
        }
        wagons.stream().filter(w -> !ordered.contains(w)).forEach(ordered::add);
        int seq = 1;
        for (TrainWagon w : ordered) {
            if (w.getSeq() != seq) {
                db.updateRec("train_wagon", Map.of("id", w.getId(), "seq", seq));
            }
            seq++;
        }
        return card(id);
    }

    // --- прицепка и отцепка

    /**
     * Прицепить вагоны к поезду фактом или планом (POST /trains/{id}/attach).
     */
    @Transactional
    public TrainCard attach(long id, ChangeBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        requireActive(id);
        Place place = place(id, body.trip(), body.routeStation(), body.station());
        requireRight(me, place, body.fact());
        List<String> numbers = numbers(body);
        OffsetDateTime time = time(body, place);
        if (place.trip() == null && body.station() == null) {
            throw ApiException.badRequest("Укажите станцию прицепки");
        }
        if (body.fact() && place.inProgress()) {
            responsibleService.check(body.responsibleUsr(), place.station(), place.trip().getLong("id"));
        }
        try {
            long seq = db.loadSqlRec(TrainSql.lastSeq(), Map.of("train", id)).getLong("last_seq");
            for (String number : numbers) {
                DbRec wagon = requireWagon(number);
                requireWorking(wagon);
                requireFree(wagon, time, null);
                if (body.fact()) {
                    requireAtStation(id, wagon, place);
                }
                Map<String, Object> row = new HashMap<>();
                row.put("train", id);
                row.put("wagon", wagon.getLong("id"));
                row.put("seq", ++seq);
                row.put("dbeg", body.fact() ? time : null);
                long trainWagon = db.insertRec("train_wagon", row);
                Map<String, Object> change = changeValues(trainWagon, TrainWagonChangeType.ATTACH, body.fact(), time,
                        place, me, body.comment());
                if (body.fact() && place.inProgress()) {
                    WagonJournal.Opened opened = wagonJournal.move(wagon.getLong("id"), time, LocationType.TRIP, null,
                            place.trip().getLong("id"), WagonStatus.IN_TRIP, body.responsibleUsr());
                    putOpened(change, opened);
                }
                db.insertRec("train_wagon_change", change);
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
     * Отцепить вагоны от поезда фактом или планом (POST /trains/{id}/detach).
     */
    @Transactional
    public TrainCard detach(long id, ChangeBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        requireActive(id);
        Place place = place(id, body.trip(), body.routeStation(), body.station());
        requireRight(me, place, body.fact());
        List<String> numbers = numbers(body);
        OffsetDateTime time = time(body, place);
        if (body.fact() && place.inProgress()) {
            responsibleService.check(body.responsibleUsr(), place.station(), place.trip().getLong("id"));
        }
        try {
            for (String number : numbers) {
                DbRec wagon = requireWagon(number);
                DbRec row = liveRow(id, wagon.getLong("id"), time);
                if (row == null) {
                    throw ApiException.conflict("Вагона " + number + " нет в поезде на это время");
                }
                if (db.loadRec("train_wagon_change", Map.of("train_wagon", row.getLong("id"),
                        "change_type", TrainWagonChangeType.DETACH, "status", TrainWagonChangeStatus.PLANNED)) != null) {
                    throw ApiException.conflict("У вагона " + number + " уже запланирована отцепка");
                }
                if (body.fact()) {
                    Place p = place.trip() == null ? stationOf(wagon, place) : place;
                    long change = db.insertRec("train_wagon_change", changeValues(row.getLong("id"),
                            TrainWagonChangeType.DETACH, true, time, p, me, body.comment()));
                    doDetach(db.loadRec("train_wagon_change", change), row, wagon, p, time, body.responsibleUsr(), me);
                } else {
                    if (row.get("dbeg") == null && !time.isAfter(plannedAttach(row))) {
                        throw ApiException.badRequest("Отцепка вагона " + number + " должна быть позже прицепки");
                    }
                    if (row.get("dbeg") != null && !time.isAfter(row.getDateTimeTZ("dbeg"))) {
                        throw ApiException.badRequest("Отцепка вагона " + number + " должна быть позже прицепки");
                    }
                    Place p = place.trip() == null && body.station() == null ? stationOf(wagon, place) : place;
                    db.insertRec("train_wagon_change", changeValues(row.getLong("id"), TrainWagonChangeType.DETACH,
                            false, time, p, me, body.comment()));
                    crewRows.releasePlanned(id, wagon.getLong("id"), time);
                }
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
     * Отметить плановую прицепку или отцепку выполненной (POST /train-changes/{id}/done).
     */
    @Transactional
    public TrainCard mark(long changeId, MarkBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec change = requireChange(changeId);
        if (change.getInt("status") != TrainWagonChangeStatus.PLANNED) {
            throw ApiException.conflict("Отмечается только запланированная прицепка или отцепка");
        }
        DbRec row = db.loadRec("train_wagon", change.getLong("train_wagon"));
        long train = row.getLong("train");
        requireActive(train);
        Place place = place(train, change.getLong("trip", null), change.getLong("route_station", null),
                change.getLong("station"));
        if (!canMark(me, change, place)) {
            throw ApiException.forbidden(place.trip() == null
                    ? "Прицепку и отцепку вне рейса отмечает планировщик вагонов"
                    : "Прицепку и отцепку рейса отмечает начальник поезда рейса");
        }
        if (place.trip() != null && !place.inProgress() && place.stopIndex() != 0) {
            throw ApiException.conflict("Прицепка и отцепка на остановке в пути отмечаются после отправления рейса");
        }
        OffsetDateTime time = place.inProgress() ? ActTime.check(body.time()) : notFuture(body.time());
        if (place.inProgress()) {
            responsibleService.check(body.responsibleUsr(), place.station(), place.trip().getLong("id"));
        }
        DbRec wagon = db.loadRec("wagon", row.getLong("wagon"));
        try {
            if (change.getInt("change_type") == TrainWagonChangeType.ATTACH) {
                requireWorking(wagon);
                requireFree(wagon, time, row.getLong("id"));
                requireAtStation(train, wagon, place);
                Map<String, Object> values = new HashMap<>();
                values.put("id", changeId);
                values.put("status", TrainWagonChangeStatus.DONE);
                values.put("fact_dt", time);
                values.put("usr", me.id());
                if (place.inProgress()) {
                    putOpened(values, wagonJournal.move(wagon.getLong("id"), time, LocationType.TRIP, null,
                            place.trip().getLong("id"), WagonStatus.IN_TRIP, body.responsibleUsr()));
                }
                db.updateRec("train_wagon_change", values);
                db.updateRec("train_wagon", Map.of("id", row.getLong("id"), "dbeg", time));
            } else {
                if (row.get("dbeg") == null) {
                    throw ApiException.conflict("Сначала отметьте прицепку вагона " + wagon.getString("number"));
                }
                if (row.get("dend") != null) {
                    throw ApiException.conflict("Вагон " + wagon.getString("number") + " уже отцеплен");
                }
                db.updateRec("train_wagon_change", Map.of("id", changeId, "usr", me.id()));
                doDetach(db.loadRec("train_wagon_change", changeId), row, wagon, place, time, body.responsibleUsr(),
                        me);
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
     * Отменить план прицепки или отцепки (POST /train-changes/{id}/cancel): статус «отменено».
     */
    @Transactional
    public TrainCard cancelPlan(long changeId) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec change = requireChange(changeId);
        if (change.getInt("status") != TrainWagonChangeStatus.PLANNED) {
            throw ApiException.conflict("Отменяется только запланированная прицепка или отцепка");
        }
        DbRec row = db.loadRec("train_wagon", change.getLong("train_wagon"));
        long train = row.getLong("train");
        Place place = place(train, change.getLong("trip", null), change.getLong("route_station", null),
                change.getLong("station"));
        if (!canCancelPlan(me, change, place)) {
            throw ApiException.forbidden("План отменяет планировщик вагонов или начальник поезда рейса");
        }
        db.updateRec("train_wagon_change", Map.of("id", changeId, "status", TrainWagonChangeStatus.CANCELLED));
        if (change.getInt("change_type") == TrainWagonChangeType.ATTACH) {
            crewRows.releasePlanned(train, row.getLong("wagon"), null);
            dissolveIfEmpty(train, OffsetDateTime.now());
        }
        evictTrainCrew(train);
        return card(train);
    }

    /**
     * Отменить выполненную прицепку или отцепку без истории (DELETE /train-changes/{id}): прицепка удаляет участие
     * вагона в поезде, отцепка - снова делает вагон вагоном поезда; записи журналов, которые открыло изменение,
     * удаляются, прежние снова текущие.
     */
    @Transactional
    public TrainCard undo(long changeId) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec change = requireChange(changeId);
        if (change.getInt("status") != TrainWagonChangeStatus.DONE) {
            throw ApiException.conflict("Отменяется только выполненная прицепка или отцепка");
        }
        DbRec row = db.loadRec("train_wagon", change.getLong("train_wagon"));
        long train = row.getLong("train");
        Long trip = change.getLong("trip", null);
        Place place = place(train, trip, change.getLong("route_station", null), change.getLong("station"));
        if (!canUndo(me, change, place)) {
            throw ApiException.forbidden(trip == null
                    ? "Прицепку и отцепку вне рейса отменяет планировщик вагонов"
                    : "Прицепку и отцепку рейса отменяет начальник поезда рейса");
        }
        WagonJournal.Opened opened = opened(change);
        long wagon = row.getLong("wagon");
        try {
            if (change.getInt("change_type") == TrainWagonChangeType.ATTACH) {
                if (row.get("dend") != null) {
                    throw ApiException.conflict("Сначала отмените отцепку вагона");
                }
                if (opened == null) {
                    requireNoTripSince(train, row.getDateTimeTZ("dbeg"));
                }
                // участие удаляется вместе с изменениями: ссылки на записи журналов уходят с ним
                db.deleteRec("train_wagon", row.getLong("id"));
                if (opened != null) {
                    wagonJournal.undo(opened);
                    crewRows.release(trip, wagon, OffsetDateTime.now());
                }
                crewRows.releasePlanned(train, wagon, null);
                dissolveIfEmpty(train, OffsetDateTime.now());
            } else {
                OffsetDateTime dend = row.getDateTimeTZ("dend");
                if (opened == null) {
                    requireNoTripSince(train, dend);
                }
                if (!busyOf(List.of(wagon), dend, row.getLong("id")).isEmpty()) {
                    throw ApiException.conflict("После отцепки вагон уже прицеплен к другому поезду");
                }
                db.deleteRec("train_wagon_change", changeId);
                Map<String, Object> values = new HashMap<>();
                values.put("id", row.getLong("id"));
                values.put("dend", null);
                db.updateRec("train_wagon", values);
                if (opened != null) {
                    wagonJournal.undo(opened);
                    wagonRun.removeSegment(trip, row.getLong("id"));
                }
                Map<String, Object> reopen = new HashMap<>();
                reopen.put("id", train);
                reopen.put("dend", null);
                db.updateRec("train", reopen);
            }
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
     * Выполнить отцепку: участие заканчивается, изменение - «выполнено». В рейсе в статусе «выполняется» вагон встаёт
     * на станцию остановки «в простое», вносится участок пробега, проводники снимаются с вагона. Вагон снимается с
     * проводников рейсов поезда в статусе «план». Поезд без вагонов и плановых прицепок расформировывается.
     */
    private void doDetach(DbRec change, DbRec row, DbRec wagon, Place place, OffsetDateTime time, Long responsible,
                          CurrentUser me) throws Exception {
        if (row.get("dbeg") == null || row.get("dend") != null) {
            throw ApiException.conflict("Вагона " + wagon.getString("number") + " нет в поезде");
        }
        if (!time.isAfter(row.getDateTimeTZ("dbeg"))) {
            throw ApiException.badRequest("Отцепка вагона " + wagon.getString("number") + " должна быть позже прицепки");
        }
        long wagonId = wagon.getLong("id");
        Map<String, Object> values = new HashMap<>();
        values.put("id", change.getLong("id"));
        values.put("status", TrainWagonChangeStatus.DONE);
        values.put("fact_dt", time);
        values.put("usr", me.id());
        if (place.inProgress()) {
            DbRec info = db.loadRec("wagon_info", Map.of("wagon", wagonId));
            Long infoTrip = info.getLong("trip", null);
            if (info.getLong("location_type") != LocationType.TRIP || infoTrip == null
                    || infoTrip != place.trip().getLong("id")) {
                throw ApiException.conflict("Вагон " + wagon.getString("number") + " не едет в поезде рейса");
            }
            putOpened(values, wagonJournal.move(wagonId, time, LocationType.STATION, place.station(), null,
                    WagonStatus.IN_IDLE, responsible));
        } else if (isInProgressTrip(row.getLong("train"))) {
            throw ApiException.conflict("Поезд в рейсе: отцепка оформляется в рейсе");
        }
        db.updateRec("train_wagon_change", values);
        db.updateRec("train_wagon", Map.of("id", row.getLong("id"), "dend", time));
        if (place.inProgress()) {
            wagonRun.addSegment(place.trip().getLong("id"), db.loadRec("train_wagon", row.getLong("id")),
                    place.routeStation());
            crewRows.release(place.trip().getLong("id"), wagonId, time);
        }
        crewRows.releasePlanned(row.getLong("train"), wagonId, null);
        dissolveIfEmpty(row.getLong("train"), time);
    }

    /**
     * Место прицепки или отцепки: рейс и остановка или станция вне рейса.
     */
    private Place place(long train, Long tripId, Long routeStation, Long station) throws Exception {
        if (tripId == null) {
            if (station == null) {
                return new Place(null, false, null, 0, -1, 0, null);
            }
            return new Place(null, false, null, station, -1, 0, null);
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
        if (rs == null || rs.getLong("route") != trip.getLong("route") || !rs.getBoolean("have_stop")) {
            throw ApiException.badRequest("Выберите остановку маршрута рейса");
        }
        List<DbRec> stops = db.loadList("route_station", Map.of("route", trip.getLong("route"), "have_stop", true));
        stops = stops.stream().sorted((a, b) -> Integer.compare(a.getInt("seq"), b.getInt("seq"))).toList();
        int index = 0;
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getLong("id") == routeStation) {
                index = i;
            }
        }
        OffsetDateTime start = trip.getDateTimeTZ("planned_dt");
        Integer minutes = rs.get("departure_min") != null ? (Integer) rs.getInt("departure_min")
                : rs.get("arrival_min") != null ? (Integer) rs.getInt("arrival_min") : null;
        DbRec first = stops.isEmpty() ? null : stops.get(0);
        int firstMin = first == null || first.get("departure_min") == null ? 0 : first.getInt("departure_min");
        OffsetDateTime stopTime = minutes == null ? start : start.plusMinutes(minutes - firstMin);
        return new Place(trip, status == TripStatus.IN_PROGRESS, routeStation, rs.getLong("station"), index,
                stops.size(), stopTime);
    }

    /** Место отцепки вне рейса: станция расположения вагона. */
    private Place stationOf(DbRec wagon, Place place) throws Exception {
        DbRec rec = db.loadSqlRec(stationSql(), Map.of("wagon", wagon.getLong("id")));
        Long station = rec == null ? null : rec.getLong("station", null);
        if (station == null) {
            throw ApiException.conflict("Вагон " + wagon.getString("number") + " в рейсе: отцепка оформляется в рейсе");
        }
        return new Place(null, false, null, station, -1, 0, place.stopTime());
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
     * умолчанию; план - указанное, а в рейсе по умолчанию - время стоянки на остановке.
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
        for (TrainWagon w : wagons(train, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(1), TrainWagon.class)) {
            if (w.getDbeg() != null && w.getDend() == null
                    && (w.getStation() == null || w.getStation() != place.station())) {
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
     * Вагон не занят в поезде с времени from: нет участия по факту или по плану, которое не закончилось к from.
     *
     * @param exceptTrainWagon участие, которое не учитывается (отмечаемое); null - все
     */
    private void requireFree(DbRec wagon, OffsetDateTime from, Long exceptTrainWagon) throws Exception {
        List<WagonBusy> busy = busyOf(List.of(wagon.getLong("id")), from, exceptTrainWagon);
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

    /** Участие вагона в поезде, которое действует в момент time по факту или плану; нет - null. */
    private DbRec liveRow(long train, long wagon, OffsetDateTime time) throws Exception {
        for (DbRec r : db.loadSql(TrainSql.busy(), Map.of("wagons", List.of(wagon), "from", time))) {
            if (r.getLong("train") == train) {
                return db.loadRec("train_wagon", r.getLong("id"));
            }
        }
        return null;
    }

    /** Плановое время прицепки участия. */
    private OffsetDateTime plannedAttach(DbRec row) throws Exception {
        DbRec c = db.loadRec("train_wagon_change", Map.of("train_wagon", row.getLong("id"),
                "change_type", TrainWagonChangeType.ATTACH, "status", TrainWagonChangeStatus.PLANNED));
        return c == null ? OffsetDateTime.MIN : c.getDateTimeTZ("planned_dt");
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

    /** Поезд расформировывается, если в нём нет вагонов по факту и плановых прицепок. */
    private void dissolveIfEmpty(long train, OffsetDateTime time) throws Exception {
        if (db.loadSqlRec(TrainSql.alive(), Map.of("train", train)).getLong("cnt") == 0) {
            db.updateRec("train", Map.of("id", train, "dend", time));
        }
    }

    private boolean isInProgressTrip(long train) throws Exception {
        return !db.loadList("trip", Map.of("train", train, "trip_status_type", TripStatus.IN_PROGRESS)).isEmpty();
    }

    private static Map<String, Object> changeValues(long trainWagon, int type, boolean fact, OffsetDateTime time,
                                                    Place place, CurrentUser me, String comment) {
        Map<String, Object> values = new HashMap<>();
        values.put("train_wagon", trainWagon);
        values.put("change_type", type);
        values.put("status", fact ? TrainWagonChangeStatus.DONE : TrainWagonChangeStatus.PLANNED);
        values.put("planned_dt", fact ? null : time);
        values.put("fact_dt", fact ? time : null);
        values.put("station", place.station());
        values.put("trip", place.trip() == null ? null : place.trip().getLong("id"));
        values.put("route_station", place.routeStation());
        values.put("usr", me.id());
        values.put("comment", comment == null || comment.isBlank() ? null : comment.trim());
        return values;
    }

    private static void putOpened(Map<String, Object> values, WagonJournal.Opened opened) {
        values.put("wagon_location", opened.location());
        values.put("wagon_status", opened.status());
        values.put("wagon_responsible", opened.responsible());
    }

    /** Записи журналов, открытые изменением; не открывало - null. */
    private static WagonJournal.Opened opened(DbRec change) {
        Long location = change.getLong("wagon_location", null);
        Long status = change.getLong("wagon_status", null);
        return location == null || status == null ? null
                : new WagonJournal.Opened(location, status, change.getLong("wagon_responsible", null));
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
     * Право на прицепку или отцепку: факт в рейсе в статусе «выполняется» - начальник поезда рейса, мастер депо
     * станции маршрута, администратор; остальное - планировщик вагонов и администратор.
     */
    private void requireRight(CurrentUser me, Place place, boolean fact) throws Exception {
        if (me.hasRole(RoleType.ADMIN)) {
            return;
        }
        if (fact && place.inProgress()) {
            if (!isChief(place.trip().getLong("id"), me) && !isMaster(place.trip(), me)) {
                throw ApiException.forbidden("Прицепку и отцепку в рейсе оформляет начальник поезда рейса или мастер "
                        + "депо станции маршрута");
            }
            return;
        }
        if (place.inProgress() && isChief(place.trip().getLong("id"), me)) {
            return;
        }
        requirePlanner(me);
    }

    /** Может ли сотрудник отметить или отменить план: план вне рейса - планировщик; план рейса - ещё начальник
     * поезда и мастер депо станции маршрута. */
    private boolean canMark(CurrentUser me, DbRec change, Place place) throws Exception {
        if (me.hasRole(RoleType.ADMIN)) {
            return true;
        }
        if (place.trip() == null) {
            return isPlanner(me);
        }
        return isChief(place.trip().getLong("id"), me) || (place.inProgress() && isMaster(place.trip(), me));
    }

    /** Может ли сотрудник отменить план: кто может отметить его выполненным, и планировщик вагонов - и в рейсе. */
    private boolean canCancelPlan(CurrentUser me, DbRec change, Place place) throws Exception {
        return canMark(me, change, place) || isPlanner(me);
    }

    /**
     * Может ли сотрудник отменить выполненную прицепку или отцепку (train_wagon_change.id): журнал вагона.
     */
    boolean canUndo(CurrentUser me, long changeId) throws Exception {
        DbRec change = db.loadRec("train_wagon_change", changeId);
        if (change == null || change.getInt("status") != TrainWagonChangeStatus.DONE) {
            return false;
        }
        Long trip = change.getLong("trip", null);
        Place place = trip == null ? new Place(null, false, null, change.getLong("station"), -1, 0, null)
                : placeOf(trip, change.getLong("route_station", null), new HashMap<>());
        return canUndo(me, change, place);
    }

    /** Может ли сотрудник отменить выполненное изменение: так же, как отметить. */
    private boolean canUndo(CurrentUser me, DbRec change, Place place) throws Exception {
        if (place.trip() != null && !place.inProgress() && isPlanner(me)) {
            return true;
        }
        return canMark(me, change, place);
    }

    // --- общее

    private TrainChange change(DbRec c, String number, CurrentUser me, Map<Long, DbRec> tripsById) throws Exception {
        TrainChange res = DbRecMapper.toObject(c, TrainChange.class);
        res.setNumber(number);
        Long trip = c.getLong("trip", null);
        res.setTrip(trip == null ? null : new TripRef(trip));
        res.setUsr(new UserRef(c.getLong("usr")));
        DbRec row = db.loadRec("train_wagon", c.getLong("train_wagon"));
        Place place = trip == null ? new Place(null, false, null, c.getLong("station"), -1, 0, null)
                : placeOf(trip, c.getLong("route_station", null), tripsById);
        boolean planned = c.getInt("status") == TrainWagonChangeStatus.PLANNED;
        boolean done = c.getInt("status") == TrainWagonChangeStatus.DONE;
        res.setCanMark(planned && row != null && canMark(me, c, place));
        res.setCanCancel(planned && row != null && canCancelPlan(me, c, place));
        res.setCanUndo(done && row != null && canUndo(me, c, place));
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
        }
        SqlQuery q = where.query(TrainSql.trains(), "order by train.name, train.id");
        List<T> res = db.loadSql(q.sql(), q.params(), clazz);
        fill(res);
        return res;
    }

    /** Вычисляемые поля строк поездов: вагоны сейчас, их станция, плановые изменения, рейсы. */
    private void fill(List<? extends TrainRow> rows) throws Exception {
        if (rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(TrainRow::getId).toList();
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> params = new HashMap<>();
        params.put("trains", ids);
        params.put("from", now);
        params.put("to", FAR);
        Map<Long, List<DbRec>> wagons = UtDb.collectGroupBy_records(db.loadSql(TrainSql.trainWagons(), params), "train");
        Map<Long, List<DbRec>> trips = trips(ids, ACTIVE);
        for (TrainRow row : rows) {
            List<DbRec> list = wagons.getOrDefault(row.getId(), List.of());
            List<TrainRowWagon> refs = new ArrayList<>();
            Set<Long> stations = new HashSet<>();
            // вид и место расположения вагонов: «вид:место»; одно на всех - вагоны в одном месте
            Set<String> places = new HashSet<>();
            Long locationType = null;
            Long place = null;
            boolean away = false;
            int planned = 0;
            for (DbRec w : list) {
                boolean in = w.get("dbeg") != null && w.get("dend") == null;
                if (in) {
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
                if (w.get("dbeg") == null || (w.get("dend") == null && w.get("fin") != null)) {
                    planned++;
                }
            }
            row.setWagons(refs);
            if (places.size() == 1) {
                row.setLocationType(locationType);
                row.setPlace(place);
            }
            row.setStation(!away && stations.size() == 1 ? stations.iterator().next() : null);
            row.setPlannedCount(planned);
            for (DbRec trip : trips.getOrDefault(row.getId(), List.of())) {
                if (trip.getLong("trip_status_type") == TripStatus.IN_PROGRESS) {
                    row.setTrip(new TripRef(trip.getLong("id")));
                } else if (row.getNextTrip() == null) {
                    row.setNextTrip(new TripRef(trip.getLong("id")));
                }
            }
        }
        refResolver.resolve(rows);
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
