package kz.zhsr.app.dto.check;

import java.math.BigDecimal;
import java.util.List;

/**
 * Значение пункта в завершённом чек-листе; заполняется одно из значений - по виду значения пункта.
 *
 * @param photos сохранённые фото пункта, которые остаются при исправлении чек-листа (check_list_value_photo.id); новые
 *               фото - части запроса
 */
public record CheckValueDraft(
        long checkListTemplateItem,
        String valueString,
        BigDecimal valueNumber,
        Long checkDictValue,
        String comment,
        List<Long> photos
) {
}
