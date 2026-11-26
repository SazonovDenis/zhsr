package kz.zhsr.app.dto.analytics;

import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Расположение вагона за период (wagon_location).
 */
@Getter
@Setter
public class GeoLocation {
    private long wagon;
    private OffsetDateTime dbeg;
    /** null - вагон там и сейчас */
    private OffsetDateTime dend;
    /** станция расположения: сама станция, станция депо или парка отстоя; у вагона в рейсе - null */
    private Long station;
    /** депо, в котором вагон; null - вагон не в депо */
    private Long depot;
    /** парк отстоя, в котором вагон; null - вагон не в парке отстоя */
    private Long yard;
    /** рейс, в котором вагон; null - вагон не в рейсе */
    private TripRef trip;
}
