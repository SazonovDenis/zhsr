package kz.zhsr.app.dto.equip;

import lombok.Getter;
import lombok.Setter;

import kz.zhsr.app.dto.ref.EquipTemplateRef;
import kz.zhsr.app.dto.ref.RequestRef;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.ref.WagonRef;

import java.time.OffsetDateTime;

/**
 * Опись экипировки - строка списка и основа карточки (equip_list).
 */
@Getter
@Setter
public class EquipListRow {
    private long id;
    private EquipTemplateRef equipListTemplate;
    private WagonRef wagon;
    private TripRef trip;
    private UserRef createdByUsr;
    private OffsetDateTime createdDt;
    private OffsetDateTime completedDt;
    /** заявка по недостаче; null - заявки нет */
    private RequestRef repairRequest;
    /** число нарушений */
    private int violationCount;
}
