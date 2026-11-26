package kz.zhsr.app.dto.analytics;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Период ответственных за вагон (wagon_responsible): факт или ожидающий план.
 */
@Getter
@Setter
public class GeoResponsible {
    private long wagon;
    /** слой - константы WagonResponsibleKind */
    private int kind;
    private OffsetDateTime dbeg;
    /** null - период открыт */
    private OffsetDateTime dend;
    /**
     * плановое время начала: у плана - dbeg, у факта по рейсу - время отработанного плана той же стадии, без плана -
     * время стадии по расписанию рейса; null - факт вне стадии рейса
     */
    private OffsetDateTime planDt;
    /** проводники рейсов из состава ответственных - id сотрудников; у плана может быть пусто */
    private List<Long> usrs = new ArrayList<>();
}
