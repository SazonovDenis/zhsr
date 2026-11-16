package kz.zhsr.app.service.ref;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefLoader;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.dto.ref.EquipTemplateRef;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Шаблон описи экипировки в данных: название на трёх языках.
 */
@Component
class EquipTemplateRefLoader extends RefLoader<EquipTemplateRef> {

    private final Db db;

    EquipTemplateRefLoader(Db db) {
        this.db = db;
    }

    @Override
    public Class<EquipTemplateRef> refClass() {
        return EquipTemplateRef.class;
    }

    @Override
    protected List<DbRec> loadBlock(List<Long> ids) throws Exception {
        return db.loadList("equip_list_template", Map.of("id", ids));
    }

    @Override
    protected void fill(EquipTemplateRef ref, DbRec rec) {
        ref.setTextRu(rec.getString("text_ru"));
        ref.setTextKk((String) rec.get("text_kk"));
        ref.setTextEn((String) rec.get("text_en"));
    }

}
