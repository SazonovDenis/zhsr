package kz.zhsr.app.dto.check;

import lombok.Getter;
import lombok.Setter;

/**
 * Вариант ответа шаблона ответов (check_dict_value).
 */
@Getter
@Setter
public class CheckDictValueRow {
    private long id;
    private long checkDict;
    private String textRu;
    private String textKk;
    private String textEn;
    private int sortOrder;
    private boolean disabled;
}
