package kz.zhsr.app.dto.trip;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Строка таблицы рейсов: вариант выбора рейса и поля таблицы.
 */
@Getter
@Setter
public class TripRow extends TripOption {
    /** вид перевозки маршрута (transport_type) */
    private long transportType;
    /** фактическое отправление; null - рейс не отправлен */
    private OffsetDateTime departedDt;
    /** фактическое прибытие; null - рейс не прибыл */
    private OffsetDateTime arrivedDt;
    /** когда отмечено отправление: 10 минут после этого отметку можно исправить или отменить; null - не отмечено */
    private OffsetDateTime departureMarkedDt;
    /** когда отмечено прибытие: 10 минут после этого отметку можно исправить или отменить; null - не отмечено */
    private OffsetDateTime arrivalMarkedDt;
    /** готовность к отправлению - у рейса в статусе «План» и «Выполняется»; у остальных null - вычисляемое */
    private TripReadiness readiness;
}
