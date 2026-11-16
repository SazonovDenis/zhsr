package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.CrewDuty;
import kz.zhsr.app.constants.TripStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Строки бригады рейса (trip_crew): строка - дежурство сотрудника при вагоне. До отправления строки правятся и
 * удаляются, с отправления - закрываются (dend) и открываются новые (dbeg).
 */
@Component
class CrewRows {

    private final Db db;
    private final CurrentUserService currentUserService;

    CrewRows(Db db, CurrentUserService currentUserService) {
        this.db = db;
        this.currentUserService = currentUserService;
    }

    /**
     * Снять вагон с проводников рейса. Рейс в статусе «план»: строка с вагоном удаляется, а если она у проводника
     * единственная - остаётся без вагона. Рейс в статусе «выполняется»: строка с вагоном закрывается временем time, а
     * проводник без других открытых строк получает строку без вагона. Вызывается внутри транзакции.
     */
    void release(long trip, long wagon, OffsetDateTime time) throws Exception {
        DbRec tripRec = db.loadRec("trip", trip);
        boolean planned = tripRec.getLong("trip_status_type") == TripStatus.PLANNED;
        List<DbRec> rows = db.loadList("trip_crew", Map.of("trip", trip, "crew_duty_type", CrewDuty.CONDUCTOR));
        for (DbRec r : rows) {
            Long rowWagon = r.getLong("wagon", null);
            if (rowWagon == null || rowWagon != wagon || (!planned && r.get("dend") != null)) {
                continue;
            }
            long usr = r.getLong("usr");
            boolean other = rows.stream().anyMatch(o -> o.getLong("usr") == usr && o.getLong("id") != r.getLong("id")
                    && (planned || o.get("dend") == null));
            if (planned) {
                if (other) {
                    db.deleteRec("trip_crew", r.getLong("id"));
                } else {
                    Map<String, Object> values = new HashMap<>();
                    values.put("id", r.getLong("id"));
                    values.put("wagon", null);
                    db.updateRec("trip_crew", values);
                }
            } else {
                db.updateRec("trip_crew", Map.of("id", r.getLong("id"), "dend", time));
                if (!other) {
                    db.insertRec("trip_crew", Map.of("trip", trip, "usr", usr, "crew_duty_type", CrewDuty.CONDUCTOR,
                            "dbeg", time));
                }
            }
            currentUserService.evict(usr);
        }
    }

    /**
     * Снять вагон с проводников во всех рейсах поезда в статусе «план» с плановым отправлением не раньше from.
     *
     * @param from null - во всех рейсах поезда в статусе «план»
     */
    void releasePlanned(long train, long wagon, OffsetDateTime from) throws Exception {
        for (DbRec trip : db.loadList("trip", Map.of("train", train, "trip_status_type", TripStatus.PLANNED))) {
            if (from == null || !trip.getDateTimeTZ("planned_dt").isBefore(from)) {
                release(trip.getLong("id"), wagon, null);
            }
        }
    }

}
