package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtCnv;
import kz.kis.kisdbtools.repository.Db;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Пробег вагона. Участок пробега (wagon_run) - путь вагона в поезде рейса от начальной станции маршрута или остановки
 * прицепки до конечной станции или остановки отцепки, км - разность route_station.km. Участок вносят прибытие рейса и
 * отцепка в рейсе, удаляет их отмена. Участок ссылается на рейс и на участие вагона в поезде - факт прицепки, с
 * которого участие началось. Общий пробег (wagon_info.run_km) - пробег на момент ввода (wagon.initial_run_km) плюс
 * сумма участков; пересчитывается заново, а не прибавлением.
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
     * @param attach       факт прицепки участия вагона в поезде (train_wagon_change.id); прицепка в этом рейсе - участок
     *                     от её остановки, иначе - от начальной станции маршрута
     * @param routeStation остановка отцепки; null - конечная станция маршрута (прибытие)
     */
    public void addSegment(long trip, long attach, Long routeStation) throws Exception {
        DbRec tripRec = db.loadRec("trip", trip);
        DbRec ends = db.loadSqlRec(TrainSql.routeEnds(), Map.of("route", tripRec.getLong("route")));
        int from = UtCnv.toInt(ends.get("first_km"));
        DbRec change = db.loadRec("train_wagon_change", attach);
        Long changeTrip = change.getLong("trip");
        if (changeTrip != null && changeTrip == trip && change.get("route_station") != null) {
            from = db.loadRec("route_station", change.getLong("route_station")).getInt("km");
        }
        int to = routeStation == null ? UtCnv.toInt(ends.get("last_km")) : db.loadRec("route_station", routeStation).getInt("km");
        long wagon = change.getLong("wagon");
        db.insertRec("wagon_run", Map.of("wagon", wagon, "trip", trip, "train_wagon_change", attach,
                "km", Math.max(0, to - from)));
        recalc(wagon);
    }

    /**
     * Удалить участок пробега вагона в рейсе и пересчитать общий пробег. Вызывается внутри транзакции.
     *
     * @param attach факт прицепки участия вагона в поезде (train_wagon_change.id)
     */
    public void removeSegment(long trip, long attach) throws Exception {
        DbRec run = db.loadRec("wagon_run", Map.of("trip", trip, "train_wagon_change", attach));
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
