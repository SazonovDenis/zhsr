package kz.zhsr.app.dto.wagon;

import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

/**
 * Расположение вагона в записи журнала (wagon_location).
 */
@Getter
@Setter
public class WagonPlace {
    /** вид расположения (location_type) */
    private long locationType;
    /** место расположения по виду: станция (station), депо (depot), парк отстоя (yard) или рейс (trip) */
    private long place;
    /** рейс, в составе которого вагон; null - вагон не в рейсе */
    private TripRef trip;
}
