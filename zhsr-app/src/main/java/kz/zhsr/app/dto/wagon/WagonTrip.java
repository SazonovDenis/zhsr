package kz.zhsr.app.dto.wagon;

import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

/**
 * Рейс в составе которого вагон: рейс в статусе «план» или «выполняется» (trip_wagon).
 */
@Getter
@Setter
public class WagonTrip {
    private TripRef trip;
    /** состояние рейса (trip_status_type) */
    private long tripStatusType;
}
