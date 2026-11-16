package kz.zhsr.app.dto.equip;

import java.math.BigDecimal;
import java.util.List;

/**
 * Позиция в завершённой описи.
 *
 * @param quantityActual фактическое количество
 * @param comment        примечание; пусто - без примечания
 * @param photos         сохранённые фото позиции, которые остаются при исправлении описи (equip_list_value_photo.id);
 *                       новые фото - части запроса
 */
public record EquipValueDraft(
        long equipListTemplateItem,
        BigDecimal quantityActual,
        String comment,
        List<Long> photos
) {
}
