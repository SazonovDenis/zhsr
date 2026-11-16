package kz.zhsr.app.dto.check;

import lombok.Getter;
import lombok.Setter;

import kz.zhsr.app.dto.ref.CheckTemplateRef;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.ref.WagonRef;

import java.time.OffsetDateTime;

/**
 * Проверка по чек-листу - строка списка и основа карточки (check_list).
 */
@Getter
@Setter
public class CheckListRow {
    private long id;
    private CheckTemplateRef checkListTemplate;
    private WagonRef wagon;
    private TripRef trip;
    private UserRef checkedUsr;
    private UserRef createdByUsr;
    private OffsetDateTime createdDt;
    private OffsetDateTime completedDt;
    /** число нарушений */
    private int violationCount;
    /** число созданных заявок */
    private int requestCount;
}
