package kz.zhsr.app.dto.analytics;

import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Рейс на карте. Цикл рейса - рейс с подготовкой: записи прицепок и отцепок рейса и записи вне рейса после прибытия
 * предыдущего рейса поезда; записи после прибытия последнего рейса поезда - его же.
 */
@Getter
@Setter
public class GeoTrip {
    private long id;
    private long route;
    private TrainRef train;
    /** состояние рейса - константы TripStatus */
    private long tripStatusType;
    private OffsetDateTime plannedDt;
    /** null - рейс не отправлен */
    private OffsetDateTime departedDt;
    /** null - рейс не прибыл */
    private OffsetDateTime arrivedDt;
    /** плановое прибытие */
    private OffsetDateTime arrival;
    /** начало цикла - прибытие предыдущего рейса поезда (фактическое, иначе ожидаемое); null - предыдущего рейса нет */
    private OffsetDateTime cycleStart;
    /** записи цикла: факты и ожидающие планы прицепок и отцепок, по времени */
    private List<GeoChange> changes = new ArrayList<>();
    /** проводники рейса */
    private List<UserRef> conductors = new ArrayList<>();
    /**
     * ход поезда по порядку: минуты от начала периода, километр линии маршрута, отклонение от расписания в минутах
     * (больше нуля - отставание); пусто - координат нет
     */
    private List<double[]> track = List.of();
}
