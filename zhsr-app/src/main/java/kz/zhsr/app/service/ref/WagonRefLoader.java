package kz.zhsr.app.service.ref;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefLoader;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.dto.ref.WagonRef;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Вагон: номер.
 */
@Component
class WagonRefLoader extends RefLoader<WagonRef> {

    private final Db db;

    WagonRefLoader(Db db) {
        this.db = db;
    }

    @Override
    public Class<WagonRef> refClass() {
        return WagonRef.class;
    }

    @Override
    protected List<DbRec> loadBlock(List<Long> ids) throws Exception {
        return db.loadList("wagon", Map.of("id", ids));
    }

    @Override
    protected void fill(WagonRef ref, DbRec rec) {
        ref.setNumber(rec.getString("number"));
        ref.setWagonType(rec.getLong("wagon_type"));
    }

}
