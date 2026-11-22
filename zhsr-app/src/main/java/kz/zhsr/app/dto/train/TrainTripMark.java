package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Рейс поезда для отметок отправления и прибытия в журнале поезда (Ф-ПОЕ-02).
 */
@Getter
@Setter
public class TrainTripMark {
    /** рейс - вычисляемое */
    private TripRef trip;
    private long tripStatusType;
    private OffsetDateTime plannedDt;
    /** фактическое отправление; null - рейс не отправлен */
    private OffsetDateTime departedDt;
    /** фактическое прибытие; null - рейс не прибыл */
    private OffsetDateTime arrivedDt;
    /** плановое прибытие: плановое отправление плюс время в пути маршрута - вычисляемое */
    private OffsetDateTime arrival;
}
