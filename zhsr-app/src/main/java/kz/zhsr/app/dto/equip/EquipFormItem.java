package kz.zhsr.app.dto.equip;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Позиция описи для заполнения: пункт шаблона, норматив для типа вагона, требуемое количество и количество из прошлой описи.
 */
@Getter
@Setter
public class EquipFormItem extends EquipItemRow {
    /** норматив для типа вагона; null - норматива нет */
    private Long equipListItemNorm;
    private String normText;
    /** способ сравнения: константы EquipQuantityKind; null - норматива нет */
    private Integer quantityKind;
    private boolean critical;
    /** требуемое количество; null - норматива нет или у модели вагона нет базы расчёта */
    private BigDecimal quantityRequired;
    /** фактическое количество в прошлой описи вагона по тому же шаблону; null - прошлой описи нет */
    private BigDecimal quantityPrevious;
}
