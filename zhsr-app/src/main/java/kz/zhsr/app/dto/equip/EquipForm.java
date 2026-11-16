package kz.zhsr.app.dto.equip;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Опись для заполнения: шаблон и позиции в порядке показа.
 */
@Getter
@Setter
public class EquipForm {
    private EquipTemplateRow template;
    private List<EquipFormItem> items = List.of();
}
