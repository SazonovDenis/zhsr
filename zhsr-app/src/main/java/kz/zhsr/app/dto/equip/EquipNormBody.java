package kz.zhsr.app.dto.equip;

import java.math.BigDecimal;

/**
 * Новый или исправленный норматив позиции описи.
 */
public record EquipNormBody(
        Long wagonType,
        BigDecimal quantityPerBasis,
        int basis,
        int quantityKind,
        BigDecimal headTailExtra,
        String normText,
        boolean critical
) {
}
