package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Вагон поезда: участие вагона в поезде (train_wagon) с текущим расположением вагона.
 */
@Getter
@Setter
public class TrainWagon {
    /** train_wagon.id */
    private long id;
    private long wagon;
    private String number;
    private long wagonType;
    /** позиция в поезде */
    private int seq;
    /** дата прицепки; null - прицепка запланирована */
    private OffsetDateTime dbeg;
    /** дата отцепки; null - вагон в поезде */
    private OffsetDateTime dend;
    private long locationType;
    /** место расположения по виду: станция, депо, парк отстоя или рейс */
    private long place;
    /** станция расположения: сама станция, станция депо или парка отстоя; у вагона в рейсе - null */
    private Long station;
    /** рейс, в котором вагон сейчас; null - вагон не в рейсе */
    private TripRef trip;
    private long wagonStatusType;
    /** пассажирский вагон: норма проводников - только для пассажирских - вычисляемое */
    private boolean passenger;
    /** плановая прицепка; null - нет */
    private TrainChange attachPlan;
    /** плановая отцепка; null - нет */
    private TrainChange detachPlan;
    /** выполненная прицепка; null - нет */
    private TrainChange attached;
    /** выполненная отцепка; null - нет */
    private TrainChange detached;
}
