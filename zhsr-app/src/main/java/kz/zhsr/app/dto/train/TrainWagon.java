package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Вагон поезда с текущими расположением и состоянием (wagon_info).
 */
@Getter
@Setter
public class TrainWagon {
    /** вагон (wagon.id) */
    private long wagon;
    private String number;
    private long wagonType;
    /** модель вагона; null - не задана */
    private Long modelType;
    private long locationType;
    /** место расположения по виду: станция, депо, парк отстоя или рейс */
    private long place;
    /** станция расположения: сама станция, станция депо или парка отстоя; у вагона в рейсе - null */
    private Long station;
    /** рейс, в котором вагон сейчас; null - вагон не в рейсе */
    private TripRef trip;
    private long wagonStatusType;
    /** с какого времени текущее состояние */
    private OffsetDateTime dbegStatus;
    /** текущая запись журнала ответственных (wagon_responsible); null - журнал ответственных пуст */
    private Long wagonResponsible;
    /** текущие ответственные за вагон, по ФИО; пусто - не назначены - вычисляемое */
    private List<UserRef> responsibleUsrs = new ArrayList<>();
    /** пассажирский вагон: норма проводников - только для пассажирских - вычисляемое */
    private boolean passenger;
}
