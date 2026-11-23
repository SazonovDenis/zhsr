package kz.zhsr.app.dto.analytics;

import kz.zhsr.app.dto.ref.WagonRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Вагон диаграммы «давно не проверяли»: последние проверки и последний рейс.
 */
@Getter
@Setter
public class UncheckedWagonRow {
    /** вагон */
    private WagonRef wagon;
    /** депо приписки (справочник Depot); null - не задано */
    private Long depot;
    /** завершение последнего чек-листа вагона; null - чек-листов не было */
    private OffsetDateTime lastCheckDt;
    /** завершение последней описи вагона; null - описей не было */
    private OffsetDateTime lastEquipDt;
    /** отправление последнего рейса вагона; null - рейсов не было */
    private OffsetDateTime lastTripDt;
}
