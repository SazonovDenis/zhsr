package kz.zhsr.app.dto.equip;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Карточка описи: строка, пункты шаблона и значения.
 */
@Getter
@Setter
public class EquipListDetail extends EquipListRow {
    private List<EquipItemRow> items = List.of();
    private List<EquipListValueRow> values = List.of();
}
