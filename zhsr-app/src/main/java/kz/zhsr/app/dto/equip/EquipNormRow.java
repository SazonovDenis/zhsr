package kz.zhsr.app.dto.equip;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Норматив позиции описи (equip_list_item_norm).
 */
@Getter
@Setter
public class EquipNormRow {
    private long id;
    private long equipListTemplateItem;
    /** тип вагона; null - для всех типов вагона */
    private Long wagonType;
    private BigDecimal quantityPerBasis;
    /** база расчёта: константы EquipBasis */
    private int basis;
    /** способ сравнения: константы EquipQuantityKind */
    private int quantityKind;
    private BigDecimal headTailExtra;
    private String normText;
    private boolean critical;
    private OffsetDateTime dbeg;
    private OffsetDateTime dend;
}
