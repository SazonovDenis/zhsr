package kz.zhsr.app.service.ref;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefLoader;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.dto.ref.TripRef;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Рейс: номер поезда и название маршрута.
 */
@Component
class TripRefLoader extends RefLoader<TripRef> {

    private final Db db;

    TripRefLoader(Db db) {
        this.db = db;
    }

    @Override
    public Class<TripRef> refClass() {
        return TripRef.class;
    }

    @Override
    protected List<DbRec> loadBlock(List<Long> ids) throws Exception {
        return db.loadSql(trips(), Map.of("ids", ids));
    }

    @Override
    protected void fill(TripRef ref, DbRec rec) {
        ref.setTrainNumber(rec.getString("train_number"));
        ref.setName(rec.getString("name"));
        ref.setPlannedDt(rec.getDateTimeTZ("planned_dt"));
    }

    private static String trips() {
        return """
                select trip.id, route.train_number, route.name, trip.planned_dt
                from trip
                join route on route.id = trip.route
                where trip.id = any(:ids)
                """;
    }

}
