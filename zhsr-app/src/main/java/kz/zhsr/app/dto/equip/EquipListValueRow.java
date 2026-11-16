package kz.zhsr.app.dto.equip;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Значение позиции описи (equip_list_value) и фото к нему.
 */
@Getter
@Setter
public class EquipListValueRow {
    private long id;
    private long equipListTemplateItem;
    private BigDecimal quantityActual;
    private BigDecimal quantityRequired;
    private Long equipListItemNorm;
    private boolean violation;
    private String comment;
    /** фото (equip_list_value_photo.id); файл - GET /equip-lists/photos/{id} */
    private List<Long> photos = List.of();
}
