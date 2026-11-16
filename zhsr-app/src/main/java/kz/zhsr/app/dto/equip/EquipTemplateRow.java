package kz.zhsr.app.dto.equip;

import lombok.Getter;
import lombok.Setter;

/**
 * Шаблон описи экипировки (equip_list_template).
 */
@Getter
@Setter
public class EquipTemplateRow {
    private long id;
    private String textRu;
    private String textKk;
    private String textEn;
    /** неисправность заявки по недостаче; null - опись без заявки */
    private Long faultType;
    private int sortOrder;
    private boolean disabled;
}
