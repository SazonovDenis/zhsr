package kz.zhsr.app.dto.check;

import lombok.Getter;
import lombok.Setter;

/**
 * Шаблон чек-листа (check_list_template).
 */
@Getter
@Setter
public class CheckTemplateRow {
    private long id;
    private String textRu;
    private String textKk;
    private String textEn;
    /** что проверяют: константы CheckObject */
    private int checkObject;
    private int sortOrder;
    private boolean disabled;
}
