package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.ActStatus;
import kz.zhsr.app.constants.CouplingType;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.constants.WagonStatus;
import kz.zhsr.app.dto.CouplingWagon;
import kz.zhsr.app.dto.TripCrew;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Отцепка и прицепка вагонов в пути (Ф-АКТ-01).
 * <p>
 * Акт составляют начальник поезда этого рейса, мастер депо, станция которого есть на маршруте рейса, и администратор; только по рейсу
 * в статусе «выполняется», на промежуточной остановке. Вагон, отцепленный актом, остаётся строкой trip_wagon, а
 * последний акт по вагону показывает, едет ли он в составе.
 */
@Service
public class CouplingService {

    /**
     * Акт отцепки или прицепки.
     *
     * @param couplingType вид акта (coupling_type)
     * @param station      станция промежуточной остановки маршрута
     * @param orderInfo    приказ (order_info); может быть пуст
     * @param reason       причина; может быть пуста
     * @param wagons       номера вагонов
     * @param actDt        время акта (act_dt), ISO с часовым поясом; null - сейчас
     */
    public record CouplingBody(long couplingType, long station, String orderInfo, String reason, List<String> wagons,
                               OffsetDateTime actDt) {
    }

    private final Db db;
    private final CurrentUserService currentUserService;
    private final TripService tripService;
    private final WagonJournal wagonJournal;

    public CouplingService(Db db, CurrentUserService currentUserService, TripService tripService,
                           WagonJournal wagonJournal) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.tripService = tripService;
        this.wagonJournal = wagonJournal;
    }

    /**
     * Вагоны для акта: для отцепки - которые едут в составе; для прицепки - станции, её депо и парка отстоя. Прицепить
     * можно только вагон на самой станции, не входящий в другой активный рейс.
     *
     * @param station станция остановки; для отцепки не нужна
     */
    public List<CouplingWagon> wagons(long id, long type, Long station) throws Exception {
        tripService.requireTrip(id);
        if (type == CouplingType.DETACH) {
            return tripService.composition(id).stream()
                    .filter(r -> inTrip(r, id))
                    .map(r -> new CouplingWagon(r.getString("number"), r.getLong("wagon_type"),
                            r.getLong("location_type"), r.getLong("place"), r.getLong("wagon_status_type"), null, true))
                    .toList();
        }
        if (type != CouplingType.ATTACH) {
            throw ApiException.badRequest("Неизвестный вид акта");
        }
        if (station == null) {
            return List.of();
        }
        return db.loadSql(TripSql.attachCandidates(), Map.of("trip", id, "station", station, "states", TripService.ACTIVE))
                .stream()
                .map(r -> {
                    String other = r.getString("other_train");
                    long location = r.getLong("location_type");
                    return new CouplingWagon(r.getString("number"), r.getLong("wagon_type"), location,
                            r.getLong("place"), r.getLong("wagon_status_type"), other,
                            location == LocationType.STATION && other == null);
                })
                .toList();
    }

    /**
     * Провести акт. Отцепленный вагон встаёт на станцию остановки, «в простое», и снимается с проводников; они
     * остаются на рейсе. Прицепленный вагон уходит «в рейс», «в пути», и добавляется в состав.
     */
    @Transactional
    public TripCrew couple(long id, CouplingBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec trip = tripService.requireTrip(id);
        TripCrew crew = tripService.load(id);
        requireRight(id, me, crew);
        if (trip.getLong("trip_status_type") != TripStatus.IN_PROGRESS) {
            throw ApiException.conflict("Акт составляется только по рейсу в статусе «Выполняется»");
        }
        boolean detach = body.couplingType() == CouplingType.DETACH;
        if (!detach && body.couplingType() != CouplingType.ATTACH) {
            throw ApiException.badRequest("Неизвестный вид акта");
        }
        DbRec stop = stop(trip.getLong("route"), crew.trip().stops(), body.station());
        List<String> numbers = body.wagons() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(body.wagons()));
        if (numbers.isEmpty()) {
            throw ApiException.badRequest("Отметьте вагоны");
        }

        Map<String, CouplingWagon> available = new HashMap<>();
        for (CouplingWagon w : wagons(id, body.couplingType(), body.station())) {
            available.put(w.number(), w);
        }
        for (String number : numbers) {
            CouplingWagon w = available.get(number);
            if (w == null || !w.available()) {
                throw ApiException.conflict(detach ? "Вагон " + number + " не едет в составе рейса"
                        : "Вагон " + number + " должен стоять на станции и не входить в другой рейс");
            }
        }

        OffsetDateTime time = ActTime.check(body.actDt());
        Map<String, Object> act = new HashMap<>();
        act.put("number", String.format("C-%05d", db.loadSqlRec(TripSql.nextActId(), Map.of()).getLong("next_id")));
        act.put("coupling_type", body.couplingType());
        act.put("trip", id);
        act.put("route_station", stop.getLong("id"));
        act.put("order_info", blankToNull(body.orderInfo()));
        act.put("reason", blankToNull(body.reason()));
        act.put("created_by_usr", me.id());
        act.put("created_dt", OffsetDateTime.now());
        act.put("act_dt", time);
        act.put("act_status_type", ActStatus.DONE);
        try {
            long actId = db.insertRec("coupling_act", act);
            int seq = 1;
            for (String number : numbers) {
                long wagon = db.loadRec("wagon", Map.of("number", number)).getLong("id");
                WagonJournal.Opened opened = detach
                        ? wagonJournal.move(wagon, time, LocationType.STATION, body.station(), null, WagonStatus.IN_IDLE)
                        : wagonJournal.move(wagon, time, LocationType.TRIP, null, id, WagonStatus.IN_TRIP);
                db.insertRec("coupling_act_wagon", Map.of("coupling_act", actId, "wagon", wagon, "seq", seq++,
                        "wagon_location", opened.location(), "wagon_status", opened.status()));
                if (detach) {
                    tripService.releaseWagon(id, wagon);
                } else if (db.loadRec("trip_wagon", Map.of("trip", id, "wagon", wagon)) == null) {
                    long last = db.loadSqlRec(TripSql.lastSeq(), Map.of("trip", id)).getLong("last_seq");
                    db.insertRec("trip_wagon", Map.of("trip", id, "wagon", wagon, "seq", last + 1));
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        return tripService.load(id);
    }

    // --- внутреннее

    private void requireRight(long id, CurrentUser me, TripCrew crew) throws Exception {
        boolean master = me.hasRole(RoleType.MASTER) && me.depotStation() != null
                && crew.trip().stops().contains(me.depotStation());
        if (!master && !me.hasRole(RoleType.ADMIN) && !tripService.isChief(id, me)) {
            throw ApiException.forbidden("Акт составляет начальник поезда рейса или мастер депо станции маршрута");
        }
    }

    /**
     * Промежуточная остановка маршрута на станции.
     */
    private DbRec stop(long route, List<Long> stops, long station) throws Exception {
        int index = stops.indexOf(station);
        if (index <= 0 || index == stops.size() - 1) {
            throw ApiException.badRequest("Выберите промежуточную остановку маршрута");
        }
        return db.loadRec("route_station", Map.of("route", route, "station", station, "have_stop", true));
    }

    private static boolean inTrip(DbRec row, long id) {
        Long trip = row.getLong("trip", null);
        return row.getLong("location_type") == LocationType.TRIP && trip != null && trip == id;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
