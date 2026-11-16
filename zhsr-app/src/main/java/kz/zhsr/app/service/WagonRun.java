package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.constants.TrainWagonChangeStatus;
import kz.zhsr.app.constants.TrainWagonChangeType;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Пробег вагона. Участок пробега (wagon_run) - путь вагона в поезде рейса от начальной станции маршрута или остановки
 * прицепки до конечной станции или остановки отцепки, км - разность route_station.km. Участок вносят прибытие рейса и
 * отцепка в рейсе, удаляет их отмена. Общий пробег (wagon_info.run_km) - пробег на момент ввода (wagon.initial_run_km)
 * плюс сумма участков; пересчитывается заново, а не прибавлением.
 */
@Service
public class WagonRun {

    private final Db db;

    public WagonRun(Db db) {
        this.db = db;
    }

    /**
     * Внести участок пробега вагона в рейсе и пересчитать общий пробег. Вызывается внутри транзакции.
     *
     * @param trainWagon   участие вагона в поезде (train_wagon)
     * @param routeStation остановка отцепки; null - конечная станция маршрута (прибытие)
     */
    public void addSegment(long trip, DbRec trainWagon, Long routeStation) throws Exception {
        DbRec tripRec = db.loadRec("trip", trip);
        long route = tripRec.getLong("route");
        DbRec ends = db.loadSqlRec(TrainSql.routeEnds(), Map.of("route", route));
        int from = ends.getInt("first_km");
        DbRec attach = db.loadRec("train_wagon_change", Map.of("train_wagon", trainWagon.getLong("id"),
                "change_type", TrainWagonChangeType.ATTACH, "status", TrainWagonChangeStatus.DONE, "trip", trip));
        if (attach != null && attach.get("route_station") != null) {
            from = db.loadRec("route_station", attach.getLong("route_station")).getInt("km");
        }
        int to = routeStation == null ? ends.getInt("last_km") : db.loadRec("route_station", routeStation).getInt("km");
        long wagon = trainWagon.getLong("wagon");
        db.insertRec("wagon_run", Map.of("wagon", wagon, "trip", trip, "train_wagon", trainWagon.getLong("id"),
                "km", Math.max(0, to - from)));
        recalc(wagon);
    }

    /**
     * Удалить участок пробега вагона в рейсе и пересчитать общий пробег. Вызывается внутри транзакции.
     */
    public void removeSegment(long trip, long trainWagon) throws Exception {
        DbRec run = db.loadRec("wagon_run", Map.of("trip", trip, "train_wagon", trainWagon));
        if (run != null) {
            db.deleteRec("wagon_run", run.getLong("id"));
            recalc(run.getLong("wagon"));
        }
    }

    /**
     * Пересчитать общий пробег вагона. Вызывается внутри транзакции.
     */
    public void recalc(long wagon) throws Exception {
        db.execSql(WagonSql.updateRunKm(), Map.of("wagon", wagon));
    }

}
