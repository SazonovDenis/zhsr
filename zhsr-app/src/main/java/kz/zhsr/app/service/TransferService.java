package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.kis.kisdbtools.repository.DbRecMapper;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.ActStatus;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.wagon.TransferPlace;
import kz.zhsr.app.dto.wagon.TransferPlaceWagons;
import kz.zhsr.app.dto.wagon.TransferRow;
import kz.zhsr.app.dto.wagon.TransferWagon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Передача вагонов между депо, станцией и парком отстоя (Ф-АКТ-02).
 * <p>
 * Откуда и куда - разные расположения одной станции: сама станция, её депо и парки отстоя; со станции на станцию не
 * передаётся. Передаёт: со станции - приёмщик, из депо или парка отстоя - мастер депо. Принимает: на станцию -
 * приёмщик, в депо или парк отстоя - мастер депо. Обе стороны работают только на станции своего депо. Акт передающей стороны ждёт приёма; акт принимающей стороны проводится сразу, с
 * указанием, кто сдал. Администратор может всё, его акт проводится сразу. Расположение вагонов меняется только при
 * приёме.
 */
@Service
public class TransferService {

    /**
     * Вагон акта.
     *
     * @param number   номер вагона
     * @param withProperty передаётся с имуществом (with_property)
     */
    public record WagonItem(String number, boolean withProperty) {
    }

    /**
     * Новый акт.
     *
     * @param fromStation      откуда: станция; ровно одно из fromStation, fromDepot, fromYard
     * @param fromDepot        откуда: депо
     * @param fromYard         откуда: парк отстоя
     * @param toStation        куда: станция; ровно одно из toStation, toDepot, toYard
     * @param toDepot          куда: депо
     * @param toYard           куда: парк отстоя
     * @param workType         вид работ (work_type)
     * @param transferredByUsr кто сдал вагоны: для акта, проведённого сразу; null - сам составитель
     * @param transferredDt    время акта, ISO с часовым поясом; null - сейчас
     * @param wagons           вагоны
     */
    public record TransferBody(Long fromStation, Long fromDepot, Long fromYard, Long toStation, Long toDepot,
                               Long toYard, long workType, Long transferredByUsr, OffsetDateTime transferredDt,
                               List<WagonItem> wagons) {

        TransferPlace from() {
            return new TransferPlace(fromStation, fromDepot, fromYard);
        }

        TransferPlace to() {
            return new TransferPlace(toStation, toDepot, toYard);
        }
    }

    /**
     * Отказ в приёме.
     *
     * @param reason причина
     */
    public record RejectBody(String reason) {
    }

    private final Db db;
    private final CurrentUserService currentUserService;
    private final Visibility visibility;
    private final WagonJournal wagonJournal;
    private final RefResolver refResolver;

    public TransferService(Db db, CurrentUserService currentUserService, Visibility visibility,
                           WagonJournal wagonJournal, RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.visibility = visibility;
        this.wagonJournal = wagonJournal;
        this.refResolver = refResolver;
    }

    /**
     * Акты, новые первыми: все при видимости всех депо, иначе акты станции депо сотрудника.
     *
     * @param states состояния (act_status_type); пусто - все
     */
    public List<TransferRow> list(List<Long> states) throws Exception {
        CurrentUser me = currentUserService.get();
        Visibility.Access access = visibility.access(me, null);
        boolean all = access.byDepot() && access.depot() == null;
        if (!all && me.depotStation() == null) {
            return List.of();
        }
        boolean byStates = states != null && !states.isEmpty();
        Map<String, Object> params = new HashMap<>();
        if (!all) {
            params.put("station", me.depotStation());
        }
        if (byStates) {
            params.put("states", states);
        }
        return rows(db.loadSql(TransferSql.acts(!all, byStates, false), params, TransferRow.class), me);
    }

    /**
     * Вагоны для акта: те, что стоят в расположении «откуда»; оно не задано - пусто.
     */
    public List<TransferWagon> wagons(TransferPlace from) throws Exception {
        currentUserService.get();
        if (from.filled() != 1) {
            return List.of();
        }
        List<TransferWagon> res = DbRecMapper.toList(candidates(from), TransferWagon.class);
        refResolver.resolve(res);
        return res;
    }

    /**
     * Сколько вагонов стоит в каждом расположении станции - для выбора «откуда»; расположений без вагонов в ответе нет.
     */
    public List<TransferPlaceWagons> placeWagons(long station) throws Exception {
        currentUserService.get();
        return db.loadSql(TransferSql.placeWagons(), Map.of("station", station), TransferPlaceWagons.class);
    }

    /**
     * Кто может сдать вагоны из расположения «откуда» - для акта, проведённого сразу; оно не задано - пусто.
     */
    public List<UserRef> givers(TransferPlace from) throws Exception {
        currentUserService.get();
        if (from.filled() != 1) {
            return List.of();
        }
        List<UserRef> res = db.loadSql(TransferSql.stationStaff(),
                Map.of("role", giverRole(from), "station", stationOf(from)), UserRef.class);
        refResolver.resolve(res);
        return res;
    }

    /**
     * Сколько актов ждёт приёма текущим сотрудником (счётчик меню «Передача»): то же правило, что у canReceive.
     */
    public int pendingCount() throws Exception {
        CurrentUser me = currentUserService.get();
        boolean admin = me.hasRole(RoleType.ADMIN);
        if (!admin && me.depotStation() == null) {
            return 0;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("pending", ActStatus.PENDING);
        params.put("admin", admin);
        params.put("receiver", me.hasRole(RoleType.RECEIVER));
        params.put("master", me.hasRole(RoleType.MASTER));
        params.put("station", me.depotStation() == null ? 0L : me.depotStation());
        return db.loadSqlRec(TransferSql.pendingForMe(), params).getLong("pending_count").intValue();
    }

    /**
     * Оформить акт: принимающая сторона и администратор проводят его сразу, передающая - оставляет ждать приёма.
     */
    @Transactional
    public TransferRow create(TransferBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        TransferPlace from = body.from();
        TransferPlace to = body.to();
        long station = requirePlaces(from, to);
        boolean immediate = canTake(me, to, station);
        if (!immediate && !canGive(me, from, station)) {
            throw ApiException.forbidden("Акт оформляют приёмщик и мастер депо на станции своего депо");
        }
        List<WagonItem> items = body.wagons() == null ? List.of() : body.wagons();
        if (items.isEmpty()) {
            throw ApiException.badRequest("Отметьте вагоны");
        }
        Map<String, DbRec> available = UtDb.collectGroupBy_record(
                candidates(from), "number");
        for (WagonItem item : items) {
            DbRec w = available.get(item.number());
            if (w == null) {
                throw ApiException.conflict("Вагон " + item.number() + " не там, откуда передаётся");
            }
            // getString отдаёт для пустого значения пустую строку, а не null: проверяется само значение
            if (w.get("pending_act") != null) {
                throw ApiException.conflict("Вагон " + item.number() + " ожидает приёма по акту "
                        + w.getString("pending_act"));
            }
        }

        OffsetDateTime time = ActTime.check(body.transferredDt());
        long giver = !immediate || body.transferredByUsr() == null ? me.id() : body.transferredByUsr();
        if (immediate && giver != me.id() && db.loadRec("usr", giver) == null) {
            throw ApiException.badRequest("Сотрудник не найден");
        }

        Map<String, Object> act = new HashMap<>();
        act.put("number", String.format("P-%05d", db.loadSqlRec(TransferSql.nextActId(), Map.of()).getLong("next_id")));
        act.put("from_station", from.station());
        act.put("from_depot", from.depot());
        act.put("from_yard", from.yard());
        act.put("to_station", to.station());
        act.put("to_depot", to.depot());
        act.put("to_yard", to.yard());
        act.put("work_type", body.workType());
        act.put("act_status_type", immediate ? ActStatus.DONE : ActStatus.PENDING);
        act.put("transferred_by_usr", giver);
        act.put("transferred_dt", time);
        if (immediate) {
            act.put("received_by_usr", me.id());
            act.put("received_dt", time);
        }
        long id;
        try {
            id = db.insertRec("transfer_act", act);
            int seq = 1;
            for (WagonItem item : items) {
                long wagon = db.loadRec("wagon", Map.of("number", item.number())).getLong("id");
                db.insertRec("transfer_act_wagon", Map.of("transfer_act", id, "wagon", wagon, "seq", seq++,
                        "with_property", item.withProperty()));
            }
            if (immediate) {
                moveWagons(id, time);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        return row(id, me);
    }

    /**
     * Принять все вагоны акта: вагоны переходят в новое расположение.
     */
    @Transactional
    public TransferRow accept(long id) throws Exception {
        CurrentUser me = currentUserService.get();
        requirePendingForMe(id, me);
        OffsetDateTime now = OffsetDateTime.now();
        db.updateRec("transfer_act", Map.of("id", id, "act_status_type", ActStatus.DONE, "received_by_usr", me.id(),
                "received_dt", now));
        moveWagons(id, now);
        return row(id, me);
    }

    /**
     * Отказать в приёме с причиной: расположение вагонов не меняется.
     */
    @Transactional
    public TransferRow reject(long id, RejectBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        requirePendingForMe(id, me);
        if (body.reason() == null || body.reason().isBlank()) {
            throw ApiException.badRequest("Укажите причину отказа");
        }
        db.updateRec("transfer_act", Map.of("id", id, "act_status_type", ActStatus.REJECTED, "received_by_usr", me.id(),
                "received_dt", OffsetDateTime.now(), "reject_reason", body.reason().trim()));
        return row(id, me);
    }

    // --- внутреннее

    /** Перевести вагоны акта в расположение «куда»; запись журнала, открытая приёмом, - в строке вагона акта. */
    private void moveWagons(long id, OffsetDateTime time) throws Exception {
        TransferPlace to = to(db.loadRec("transfer_act", id));
        for (DbRec row : db.loadList("transfer_act_wagon", Map.of("transfer_act", id))) {
            long opened = wagonJournal.moveLocation(row.getLong("wagon"), time, to.locationType(), to.station(),
                    to.depot(), to.yard());
            db.updateRec("transfer_act_wagon", Map.of("id", row.getLong("id"), "wagon_location", opened));
        }
    }

    /** Вагоны, которые стоят в расположении; с номером акта, который ждёт приёма по вагону. */
    private List<DbRec> candidates(TransferPlace place) throws Exception {
        Map<String, Object> params = Map.of("pending", ActStatus.PENDING);
        if (place.station() != null) {
            return db.loadSql(TransferSql.stationWagons(), with(params, "station", place.station()));
        }
        if (place.depot() != null) {
            return db.loadSql(TransferSql.depotWagons(), with(params, "depot", place.depot()));
        }
        return db.loadSql(TransferSql.yardWagons(), with(params, "yard", place.yard()));
    }

    private static Map<String, Object> with(Map<String, Object> params, String name, Object value) {
        Map<String, Object> res = new HashMap<>(params);
        res.put(name, value);
        return res;
    }

    /**
     * Проверить «откуда» и «куда»: по одному расположению, разные, не обе станции, на одной станции.
     *
     * @return станция акта
     */
    private long requirePlaces(TransferPlace from, TransferPlace to) throws Exception {
        if (from.filled() != 1) {
            throw ApiException.badRequest("Укажите, откуда передаются вагоны: станция, депо или парк отстоя");
        }
        if (to.filled() != 1) {
            throw ApiException.badRequest("Укажите, куда передаются вагоны: станция, депо или парк отстоя");
        }
        if (from.station() != null && to.station() != null) {
            throw ApiException.badRequest("Со станции на станцию вагоны не передаются");
        }
        if (from.equals(to)) {
            throw ApiException.badRequest("Откуда и куда - одно и то же расположение");
        }
        long station = stationOf(from);
        if (stationOf(to) != station) {
            throw ApiException.badRequest("Откуда и куда - расположения разных станций");
        }
        return station;
    }

    /** Станция расположения: сама станция или станция депо или парка отстоя. */
    private long stationOf(TransferPlace place) throws Exception {
        if (place.station() != null) {
            return place.station();
        }
        DbRec rec = place.depot() != null ? db.loadRec("depot", place.depot()) : db.loadRec("yard", place.yard());
        if (rec == null) {
            throw ApiException.badRequest("Депо или парк отстоя не найдены");
        }
        Long station = rec.getLong("station", null);
        if (station == null) {
            throw ApiException.badRequest("У депо или парка отстоя не задана станция");
        }
        return station;
    }

    private static TransferPlace from(DbRec act) {
        return new TransferPlace(act.getLong("from_station", null), act.getLong("from_depot", null),
                act.getLong("from_yard", null));
    }

    private static TransferPlace to(DbRec act) {
        return new TransferPlace(act.getLong("to_station", null), act.getLong("to_depot", null),
                act.getLong("to_yard", null));
    }

    private DbRec requirePendingForMe(long id, CurrentUser me) throws Exception {
        DbRec act = db.loadRec("transfer_act", id);
        if (act == null) {
            throw ApiException.notFound("Акт не найден");
        }
        if (act.getLong("act_status_type") != ActStatus.PENDING) {
            throw ApiException.conflict("Акт не ожидает приёма");
        }
        if (!canTake(me, to(act), stationOf(from(act)))) {
            throw ApiException.forbidden("Принимает другая сторона на станции своего депо");
        }
        return act;
    }

    /** Кто сдаёт: со станции - приёмщик, из депо или парка отстоя - мастер депо. */
    private static long giverRole(TransferPlace from) {
        return from.station() != null ? RoleType.RECEIVER : RoleType.MASTER;
    }

    /** Кто принимает: на станцию - приёмщик, в депо или парк отстоя - мастер депо. */
    private static long takerRole(TransferPlace to) {
        return to.station() != null ? RoleType.RECEIVER : RoleType.MASTER;
    }

    private static boolean canGive(CurrentUser me, TransferPlace from, long station) {
        return me.hasRole(giverRole(from)) && Long.valueOf(station).equals(me.depotStation());
    }

    private static boolean canTake(CurrentUser me, TransferPlace to, long station) {
        return me.hasRole(RoleType.ADMIN)
                || (me.hasRole(takerRole(to)) && Long.valueOf(station).equals(me.depotStation()));
    }

    private TransferRow row(long id, CurrentUser me) throws Exception {
        return rows(db.loadSql(TransferSql.acts(false, false, true), Map.of("id", id), TransferRow.class), me).get(0);
    }

    private List<TransferRow> rows(List<TransferRow> acts, CurrentUser me) throws Exception {
        if (acts.isEmpty()) {
            return acts;
        }
        Set<Long> ids = acts.stream().map(TransferRow::getId).collect(Collectors.toSet());
        Map<Long, List<TransferRow.Wagon>> wagons = new HashMap<>();
        for (DbRec r : db.loadSql(TransferSql.actWagons(), Map.of("acts", ids))) {
            wagons.computeIfAbsent(r.getLong("transfer_act"), k -> new ArrayList<>())
                    .add(DbRecMapper.toObject(r, TransferRow.Wagon.class));
        }
        for (TransferRow a : acts) {
            a.setWagons(wagons.getOrDefault(a.getId(), List.of()));
            a.setCanReceive(a.getActStatusType() == ActStatus.PENDING
                    && canTake(me, new TransferPlace(a.getToStation(), a.getToDepot(), a.getToYard()),
                    a.getStation()));
        }
        refResolver.resolve(acts);
        return acts;
    }

}
