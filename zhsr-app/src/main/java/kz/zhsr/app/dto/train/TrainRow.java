package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Строка таблицы поездов (Ф-ПОЕ-00).
 */
@Getter
@Setter
public class TrainRow {
    private long id;
    /** обозначение */
    private String name;
    /** когда сформирован */
    private OffsetDateTime dbeg;
    /** когда расформирован; null - поезд действует */
    private OffsetDateTime dend;
    /** номера вагонов поезда сейчас по порядку - вычисляемое */
    private List<String> wagons = List.of();
    /** станция вагонов поезда: все вагоны на ней или в её депо и парках отстоя; null - вагоны на разных станциях,
     * в рейсе или вагонов нет - вычисляемое */
    private Long station;
    /** число плановых прицепок и отцепок - вычисляемое */
    private int plannedCount;
    /** рейс поезда в статусе «выполняется»; null - нет - вычисляемое */
    private TripRef trip;
    /** ближайший рейс поезда в статусе «план»; null - нет - вычисляемое */
    private TripRef nextTrip;
}
