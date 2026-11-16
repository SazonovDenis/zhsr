package kz.zhsr.app.service.ref;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefLoader;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.dto.ref.CheckTemplateRef;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Шаблон чек-листа в данных: название на трёх языках.
 */
@Component
class CheckTemplateRefLoader extends RefLoader<CheckTemplateRef> {

    private final Db db;

    CheckTemplateRefLoader(Db db) {
        this.db = db;
    }

    @Override
    public Class<CheckTemplateRef> refClass() {
        return CheckTemplateRef.class;
    }

    @Override
    protected List<DbRec> loadBlock(List<Long> ids) throws Exception {
        return db.loadList("check_list_template", Map.of("id", ids));
    }

    @Override
    protected void fill(CheckTemplateRef ref, DbRec rec) {
        ref.setTextRu(rec.getString("text_ru"));
        ref.setTextKk((String) rec.get("text_kk"));
        ref.setTextEn((String) rec.get("text_en"));
    }

}
