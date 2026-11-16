package kz.zhsr.app.dto.check;

import lombok.Getter;
import lombok.Setter;

/**
 * Шаблон ответов чек-листа (check_dict).
 */
@Getter
@Setter
public class CheckDictRow {
    private long id;
    private String textRu;
    private String textKk;
    private String textEn;
    private int sortOrder;
    private boolean disabled;
}
