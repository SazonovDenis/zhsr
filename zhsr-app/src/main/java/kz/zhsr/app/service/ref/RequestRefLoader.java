package kz.zhsr.app.service.ref;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefLoader;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.dto.ref.RequestRef;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Заявка на ремонт в данных: номер.
 */
@Component
class RequestRefLoader extends RefLoader<RequestRef> {

    private final Db db;

    RequestRefLoader(Db db) {
        this.db = db;
    }

    @Override
    public Class<RequestRef> refClass() {
        return RequestRef.class;
    }

    @Override
    protected List<DbRec> loadBlock(List<Long> ids) throws Exception {
        return db.loadList("repair_request", Map.of("id", ids));
    }

    @Override
    protected void fill(RequestRef ref, DbRec rec) {
        ref.setNumber(rec.getString("number"));
    }

}
