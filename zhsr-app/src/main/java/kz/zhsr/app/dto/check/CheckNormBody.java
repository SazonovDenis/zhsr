package kz.zhsr.app.dto.check;

import java.math.BigDecimal;
import java.util.List;

/**
 * Новый или исправленный норматив пункта чек-листа.
 */
public record CheckNormBody(
        Long wagonType,
        boolean required,
        BigDecimal numMin,
        BigDecimal numMax,
        boolean critical,
        Long faultType,
        List<Long> checkDictValues
) {
}
