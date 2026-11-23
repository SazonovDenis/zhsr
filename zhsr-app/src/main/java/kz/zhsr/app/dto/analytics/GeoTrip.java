package kz.zhsr.app.dto.analytics;

import kz.zhsr.app.dto.ref.TrainRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Рейс на карте.
 */
@Getter
@Setter
public class GeoTrip {
    private long id;
    private long route;
    private TrainRef train;
    private OffsetDateTime plannedDt;
    private OffsetDateTime departedDt;
    /** null - рейс в пути */
    private OffsetDateTime arrivedDt;
    /**
     * ход поезда по порядку: минуты от начала периода, километр линии маршрута, отклонение от расписания в минутах
     * (больше нуля - отставание); пусто - координат нет
     */
    private List<double[]> track = List.of();
}
