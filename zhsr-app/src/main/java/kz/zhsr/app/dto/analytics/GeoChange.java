package kz.zhsr.app.dto.analytics;

import kz.zhsr.app.dto.ref.WagonRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Прицепка или отцепка вагона (train_wagon_change) в цикле рейса.
 */
@Getter
@Setter
public class GeoChange {
    private long id;
    private WagonRef wagon;
    /** слой - константы TrainWagonKind */
    private int kind;
    /** вид - константы TrainWagonChangeType */
    private int changeType;
    /** время записи: у плана плановое, у факта фактическое */
    private OffsetDateTime dt;
    /** с какого времени запись действует: у ожидающего плана - dt, но не раньше последнего факта поезда */
    private OffsetDateTime moment;
    /** плановое время: у ожидающего плана - dt, у факта по плану - время плана; null - факт без плана */
    private OffsetDateTime planDt;
    /** станция - id справочника Station */
    private long station;
}
