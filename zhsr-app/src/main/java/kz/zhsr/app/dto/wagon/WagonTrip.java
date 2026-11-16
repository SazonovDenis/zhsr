package kz.zhsr.app.dto.wagon;

import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

/**
 * Рейс поезда вагона: рейс в статусе «план» или «выполняется».
 */
@Getter
@Setter
public class WagonTrip {
    private TripRef trip;
    /** состояние рейса (trip_status_type) */
    private long tripStatusType;
}
