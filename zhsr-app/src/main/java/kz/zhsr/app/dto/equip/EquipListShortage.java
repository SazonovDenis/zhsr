package kz.zhsr.app.dto.equip;

import kz.zhsr.app.dto.ref.EquipTemplateRef;
import kz.zhsr.app.dto.ref.WagonRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Последняя опись вагона по шаблону и недостача в ней - готовность вагона к рейсу.
 */
@Getter
@Setter
public class EquipListShortage {
    private long id;
    private EquipTemplateRef equipListTemplate;
    private WagonRef wagon;
    private OffsetDateTime createdDt;
    /** сколько позиций в недостаче; 0 - недостачи нет */
    private int shortageCount;
}
