package kz.zhsr.app.dto.trip;

import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

/**
 * Вагон в акте отцепки или прицепки.
 */
@Getter
@Setter
public class CouplingWagon {
    private String number;
    private long wagonType;
    private long locationType;
    private long place;
    private long wagonStatusType;
    /** другой активный рейс, в составе которого вагон; null - такого рейса нет */
    private TripRef otherTrip;
    /** можно отцепить или прицепить; иначе показан, но отметить нельзя */
    private boolean available;
}
