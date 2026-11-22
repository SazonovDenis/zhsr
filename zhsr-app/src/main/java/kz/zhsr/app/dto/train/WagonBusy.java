package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Занятость вагона в поезде: вагон в поезде по факту или по плану с dbeg по dend.
 */
@Getter
@Setter
public class WagonBusy {
    private String number;
    private TrainRef train;
    /** с какого времени вагон в поезде: по факту или в ожидаемом составе */
    private OffsetDateTime dbeg;
    /** до какого времени; null - без конца */
    private OffsetDateTime dend;
    /** рейс поезда в это время; null - поезд не в рейсе */
    private TripRef trip;
}
