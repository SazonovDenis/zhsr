package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.WagonRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Неготовность вагона к прицепке или к рейсу - вычисляемое (WagonProblems). Расположение (виды «другая станция» и «в
 * депо или парке») - поля locationType, place, station и needStation; занятость в другом поезде - поля train, trip, dbeg,
 * dend и fact, на каждый другой поезд - своя запись.
 */
@Getter
@Setter
public class WagonProblem {
    private WagonRef wagon;
    /** константы WagonProblemType */
    private int wagonProblemType;

    /** когда нужен вагон: начало промежутка, в котором нужен вагон */
    private OffsetDateTime needDt;

    /** где будет вагон: вид расположения (LocationType); null - неизвестно */
    private Long locationType;
    /** id станции, депо, парка отстоя или рейса по locationType */
    private Long place;
    /** станция расположения; null - вагон в рейсе, станция неизвестна */
    private Long station;
    /** на какой станции вагон нужен */
    private Long needStation;

    /** другой поезд */
    private TrainRef train;
    /** рейс другого поезда в это время; null - поезд не в рейсе */
    private TripRef trip;
    /** с какого времени вагон в другом поезде: прицепка */
    private OffsetDateTime dbeg;
    /** когда вагон освободится: запланированная отцепка; null - отцепка не запланирована */
    private OffsetDateTime dend;
    /** прицепка к другому поезду выполнена фактом, а не запланирована */
    private boolean fact;
}
