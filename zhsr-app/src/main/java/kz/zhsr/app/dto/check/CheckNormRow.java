package kz.zhsr.app.dto.check;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Норматив пункта чек-листа (check_list_item_norm) и допустимые варианты ответа.
 */
@Getter
@Setter
public class CheckNormRow {
    private long id;
    private long checkListTemplateItem;
    /** тип вагона; null - для всех типов вагона */
    private Long wagonType;
    private boolean required;
    private BigDecimal numMin;
    private BigDecimal numMax;
    private boolean critical;
    /** неисправность заявки по нарушению; null - нарушение без заявки */
    private Long faultType;
    private OffsetDateTime dbeg;
    private OffsetDateTime dend;
    /** допустимые варианты ответа (check_list_item_norm_value) */
    private List<Long> checkDictValues = List.of();
}
