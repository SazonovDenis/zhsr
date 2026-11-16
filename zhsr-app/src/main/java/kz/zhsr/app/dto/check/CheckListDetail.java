package kz.zhsr.app.dto.check;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Карточка проверки: строка, пункты шаблона, значения и варианты ответа.
 */
@Getter
@Setter
public class CheckListDetail extends CheckListRow {
    private List<CheckItemRow> items = List.of();
    private List<CheckListValueRow> values = List.of();
    private List<CheckDictValueRow> dictValues = List.of();
}
