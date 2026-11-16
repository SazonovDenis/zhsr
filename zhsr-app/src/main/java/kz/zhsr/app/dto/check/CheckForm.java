package kz.zhsr.app.dto.check;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Чек-лист для заполнения: шаблон, пункты в порядке показа и варианты ответа их шаблонов ответов.
 */
@Getter
@Setter
public class CheckForm {
    private CheckTemplateRow template;
    private List<CheckFormItem> items = List.of();
    private List<CheckDictValueRow> dictValues = List.of();
}
