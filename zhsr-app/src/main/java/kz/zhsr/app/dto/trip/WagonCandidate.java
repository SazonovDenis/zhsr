package kz.zhsr.app.dto.trip;

import lombok.Getter;
import lombok.Setter;

/**
 * Вагон для состава рейса.
 */
@Getter
@Setter
public class WagonCandidate {
    private String number;
    private long wagonType;
    private long locationType;
    private long place;
    private long wagonStatusType;
}
