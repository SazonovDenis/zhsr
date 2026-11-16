package kz.zhsr.app.dto.ref;

import kz.kis.kisdbtools.ref.Ref;
import lombok.Getter;
import lombok.Setter;

/**
 * Шаблон чек-листа в данных: название на трёх языках.
 */
@Getter
@Setter
public class CheckTemplateRef extends Ref {
    /** название на русском */
    private String textRu;
    /** название на казахском; null - не задано */
    private String textKk;
    /** название на английском; null - не задано */
    private String textEn;

    public CheckTemplateRef() {
    }

    public CheckTemplateRef(long id) {
        super(id);
    }
}
