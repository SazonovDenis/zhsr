package kz.zhsr.app.dto.check;

import lombok.Getter;
import lombok.Setter;

import kz.zhsr.app.dto.ref.RequestRef;

import java.math.BigDecimal;
import java.util.List;

/**
 * Значение пункта проверки (check_list_value) и фото к нему.
 */
@Getter
@Setter
public class CheckListValueRow {
    private long id;
    private long checkListTemplateItem;
    private String valueString;
    private BigDecimal valueNumber;
    private Long checkDictValue;
    private String comment;
    private Long checkListItemNorm;
    private boolean violation;
    private RequestRef repairRequest;
    /** фото (check_list_value_photo.id); файл - GET /check-lists/photos/{id} */
    private List<Long> photos = List.of();
}
