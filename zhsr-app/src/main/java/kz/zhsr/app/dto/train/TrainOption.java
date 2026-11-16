package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

/**
 * Поезд как вариант выбора в рейсе (Ф-РЕЙ-01).
 */
@Getter
@Setter
public class TrainOption extends TrainRow {
    /** рейс, в котором поезд занят во время выбранного рейса; null - свободен - вычисляемое */
    private TripRef busyTrip;
}
