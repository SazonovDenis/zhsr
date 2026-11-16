package kz.zhsr.app.dto.check;

import lombok.Getter;
import lombok.Setter;

/**
 * Пункт шаблона чек-листа (check_list_template_item): раздел или пункт со значением.
 */
@Getter
@Setter
public class CheckItemRow {
    private long id;
    private long checkListTemplate;
    /** раздел, в который входит пункт; null - пункт верхнего уровня */
    private Long parentItem;
    private String textRu;
    private String textKk;
    private String textEn;
    /** вид значения: константы CheckValueKind; null - раздел */
    private Integer valueKind;
    /** шаблон ответов - у пункта с вариантом ответа */
    private Long checkDict;
    private int sortOrder;
    private boolean disabled;
}
