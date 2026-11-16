package kz.zhsr.app.dto.equip;

import lombok.Getter;
import lombok.Setter;

/**
 * Пункт шаблона описи (equip_list_template_item): раздел или позиция.
 */
@Getter
@Setter
public class EquipItemRow {
    private long id;
    private long equipListTemplate;
    /** раздел, в который входит пункт; null - пункт верхнего уровня */
    private Long parentItem;
    private String textRu;
    private String textKk;
    private String textEn;
    /** единица измерения; null - раздел */
    private String unit;
    private int sortOrder;
    private boolean disabled;
}
