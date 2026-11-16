package kz.zhsr.app.service.ref;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefLoader;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.dto.ref.TrainRef;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Поезд: обозначение.
 */
@Component
class TrainRefLoader extends RefLoader<TrainRef> {

    private final Db db;

    TrainRefLoader(Db db) {
        this.db = db;
    }

    @Override
    public Class<TrainRef> refClass() {
        return TrainRef.class;
    }

    @Override
    protected List<DbRec> loadBlock(List<Long> ids) throws Exception {
        return db.loadList("train", Map.of("id", ids));
    }

    @Override
    protected void fill(TrainRef ref, DbRec rec) {
        ref.setName(rec.getString("name"));
    }

}
