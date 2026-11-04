package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.ActStatus;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.TransferDirection;
import kz.zhsr.app.dto.StaffCandidate;
import kz.zhsr.app.dto.TransferRow;
import kz.zhsr.app.dto.UserRef;
import kz.zhsr.app.dto.TransferWagon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Передача вагонов в депо и парк отстоя и приём из них (Ф-АКТ-02).
 * <p>
 * Передаёт: со станции - приёмщик, из депо или парка отстоя - мастер депо. Принимает другая сторона. Обе работают
 * только на станции своего депо. Акт передающей стороны ждёт приёма; акт принимающей стороны проводится сразу, с
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
     * @param transferDirectionType направление (transfer_direction_type)
     * @param station               станция передачи
     * @param depot                 депо; ровно одно из depot, yard
     * @param yard                  парк отстоя
     * @param workType              вид работ (work_type)
     * @param transferredByUsr      кто сдал вагоны: для акта, проведённого сразу; null - сам составитель
     * @param transferredDt         время акта, ISO с часовым поясом; null - сейчас
     * @param wagons                вагоны
     */
    public record TransferBody(long transferDirectionType, long station, Long depot, Long yard, long workType,
                               Long transferredByUsr, OffsetDateTime transferredDt, List<WagonItem> wagons) {
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

    public TransferService(Db db, CurrentUserService currentUserService, Visibility visibility,
                           WagonJournal wagonJournal) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.visibility = visibility;
        this.wagonJournal = wagonJournal;
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
        return rows(db.loadSql(TransferSql.acts(!all, byStates, false), params), me);
    }

    /**
     * Вагоны для акта: со станции - на станции, из депо или парка отстоя - там.
     */
    public List<TransferWagon> wagons(long direction, Long station, Long depot, Long yard) throws Exception {
        currentUserService.get();
        return candidates(direction, station, depot, yard).stream()
                .map(r -> new TransferWagon(r.getString("number"), r.getLong("wagon_type"), r.getLong("location_type"),
                        r.getLong("place"), r.getLong("wagon_status_type"), r.getString("pending_act")))
                .toList();
    }

    /**
     * Кто может сдать вагоны на станции в этом направлении - для акта, проведённого сразу.
     */
    public List<StaffCandidate> givers(long direction, long station) throws Exception {
        currentUserService.get();
        return db.loadSql(TransferSql.stationStaff(), Map.of("role", giverRole(direction), "station", station)).stream()
                .map(r -> new StaffCandidate(r.getLong("id"), CurrentUser.shortName(r.getString("full_name"))))
                .toList();
    }

    /**
     * Оформить акт: принимающая сторона и администратор проводят его сразу, передающая - оставляет ждать приёма.
     */
    @Transactional
    public TransferRow create(TransferBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        long direction = body.transferDirectionType();
        boolean immediate = canTake(me, direction, body.station());
        if (!immediate && !canGive(me, direction, body.station())) {
            throw ApiException.forbidden("Акт оформляют приёмщик и мастер депо на станции своего депо");
        }
        if ((body.depot() == null) == (body.yard() == null)) {
            throw ApiException.badRequest("Укажите депо или парк отстоя");
        }
        requirePlaceAtStation(body);
        List<WagonItem> items = body.wagons() == null ? List.of() : body.wagons();
        if (items.isEmpty()) {
            throw ApiException.badRequest("Отметьте вагоны");
        }
        Map<String, DbRec> available = new HashMap<>();
        for (DbRec r : candidates(direction, body.station(), body.depot(), body.yard())) {
            available.put(r.getString("number"), r);
        }
        for (WagonItem item : items) {
            DbRec w = available.get(item.number());
            if (w == null) {
                throw ApiException.conflict("Вагон " + item.number() + " не там, откуда передаётся");
            }
            if (w.getString("pending_act") != null) {
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
        act.put("transfer_direction_type", direction);
        act.put("station", body.station());
        act.put("depot", body.depot());
        act.put("yard", body.yard());
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

    private void moveWagons(long id, OffsetDateTime time) throws Exception {
        DbRec act = db.loadRec("transfer_act", id);
        boolean toDepot = act.getLong("transfer_direction_type") == TransferDirection.TO_DEPOT;
        Long depot = act.getLong("depot", null);
        Long yard = act.getLong("yard", null);
        for (DbRec row : db.loadList("transfer_act_wagon", Map.of("transfer_act", id))) {
            long opened = toDepot
                    ? wagonJournal.moveLocation(row.getLong("wagon"), time,
                            depot != null ? LocationType.DEPOT : LocationType.YARD, null, depot, yard)
                    : wagonJournal.moveLocation(row.getLong("wagon"), time, LocationType.STATION,
                            act.getLong("station"), null, null);
            db.updateRec("transfer_act_wagon", Map.of("id", row.getLong("id"), "wagon_location", opened));
        }
    }

    private List<DbRec> candidates(long direction, Long station, Long depot, Long yard) throws Exception {
        requireDirection(direction);
        if (direction == TransferDirection.TO_DEPOT) {
            if (station == null) {
                return List.of();
            }
            return db.loadSql(TransferSql.stationWagons(), Map.of("station", station, "pending", ActStatus.PENDING));
        }
        if (depot != null) {
            return db.loadSql(TransferSql.depotWagons(), Map.of("depot", depot, "pending", ActStatus.PENDING));
        }
        if (yard != null) {
            return db.loadSql(TransferSql.yardWagons(), Map.of("yard", yard, "pending", ActStatus.PENDING));
        }
        return List.of();
    }

    private void requirePlaceAtStation(TransferBody body) throws Exception {
        DbRec place = body.depot() != null ? db.loadRec("depot", body.depot()) : db.loadRec("yard", body.yard());
        if (place == null || place.getLong("station", null) == null
                || place.getLong("station") != body.station()) {
            throw ApiException.badRequest("Депо или парк отстоя не при этой станции");
        }
    }

    private DbRec requirePendingForMe(long id, CurrentUser me) throws Exception {
        DbRec act = db.loadRec("transfer_act", id);
        if (act == null) {
            throw ApiException.notFound("Акт не найден");
        }
        if (act.getLong("act_status_type") != ActStatus.PENDING) {
            throw ApiException.conflict("Акт не ожидает приёма");
        }
        if (!canTake(me, act.getLong("transfer_direction_type"), act.getLong("station"))) {
            throw ApiException.forbidden("Принимает другая сторона на станции своего депо");
        }
        return act;
    }

    private static void requireDirection(long direction) {
        if (direction != TransferDirection.TO_DEPOT && direction != TransferDirection.FROM_DEPOT) {
            throw ApiException.badRequest("Неизвестное направление");
        }
    }

    private static long giverRole(long direction) {
        requireDirection(direction);
        return direction == TransferDirection.TO_DEPOT ? RoleType.RECEIVER : RoleType.MASTER;
    }

    private static long takerRole(long direction) {
        requireDirection(direction);
        return direction == TransferDirection.TO_DEPOT ? RoleType.MASTER : RoleType.RECEIVER;
    }

    private static boolean canGive(CurrentUser me, long direction, long station) {
        return me.hasRole(giverRole(direction)) && Long.valueOf(station).equals(me.depotStation());
    }

    private static boolean canTake(CurrentUser me, long direction, long station) {
        return me.hasRole(RoleType.ADMIN)
                || (me.hasRole(takerRole(direction)) && Long.valueOf(station).equals(me.depotStation()));
    }

    private TransferRow row(long id, CurrentUser me) throws Exception {
        return rows(db.loadSql(TransferSql.acts(false, false, true), Map.of("id", id)), me).get(0);
    }

    private List<TransferRow> rows(List<DbRec> acts, CurrentUser me) throws Exception {
        if (acts.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        for (DbRec a : acts) {
            ids.add(a.getLong("id"));
        }
        Map<Long, List<TransferRow.Wagon>> wagons = new HashMap<>();
        for (DbRec r : db.loadSql(TransferSql.actWagons(), Map.of("acts", ids))) {
            wagons.computeIfAbsent(r.getLong("transfer_act"), k -> new ArrayList<>())
                    .add(new TransferRow.Wagon(r.getString("number"), r.getBoolean("with_property")));
        }
        List<TransferRow> res = new ArrayList<>(acts.size());
        for (DbRec a : acts) {
            long status = a.getLong("act_status_type");
            long direction = a.getLong("transfer_direction_type");
            Long receivedBy = a.getLong("received_by_usr");
            res.add(new TransferRow(a.getLong("id"), a.getString("number"), direction, a.getLong("station"),
                    a.getLong("depot", null), a.getLong("yard", null), a.getLong("work_type"), status,
                    new UserRef(a.getLong("transferred_by_usr"), CurrentUser.shortName(a.getString("transferred_by"))),
                    a.getDateTimeTZ("transferred_dt"),
                    receivedBy == null ? null : new UserRef(receivedBy, CurrentUser.shortName(a.getString("received_by"))),
                    a.getDateTimeTZ("received_dt"),
                    a.getString("reject_reason"), wagons.get(a.getLong("id")),
                    status == ActStatus.PENDING && canTake(me, direction, a.getLong("station"))));
        }
        return res;
    }

}
