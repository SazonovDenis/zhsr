package kz.zhsr.app.dto.check;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Пункт чек-листа для заполнения: пункт шаблона и подсказки норматива, подобранного для объекта проверки.
 */
@Getter
@Setter
public class CheckFormItem extends CheckItemRow {
    /** норматив пункта для объекта проверки; null - норматива нет */
    private Long checkListItemNorm;
    private boolean required;
    private BigDecimal numMin;
    private BigDecimal numMax;
    private boolean critical;
    /** допустимые варианты ответа; пусто - любой вариант не нарушение */
    private List<Long> checkDictValues = List.of();
    /** неисправность заявки по нарушению; null - нарушение без заявки (заявка - только при проверке вагона) */
    private Long faultType;
}
