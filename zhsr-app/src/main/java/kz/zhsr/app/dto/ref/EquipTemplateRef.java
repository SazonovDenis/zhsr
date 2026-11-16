package kz.zhsr.app.dto.ref;

import kz.kis.kisdbtools.ref.Ref;
import lombok.Getter;
import lombok.Setter;

/**
 * Шаблон описи экипировки в данных: название на трёх языках.
 */
@Getter
@Setter
public class EquipTemplateRef extends Ref {
    /** название на русском */
    private String textRu;
    /** название на казахском; null - не задано */
    private String textKk;
    /** название на английском; null - не задано */
    private String textEn;

    public EquipTemplateRef() {
    }

    public EquipTemplateRef(long id) {
        super(id);
    }
}
