package kz.zhsr.app.dto.ref;

import kz.kis.kisdbtools.ref.Ref;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Рейс в данных.
 */
@Getter
@Setter
public class TripRef extends Ref {
    /** номер поезда маршрута */
    private String trainNumber;
    /** название маршрута */
    private String name;
    /** плановое отправление */
    private OffsetDateTime plannedDt;

    public TripRef() {
    }

    public TripRef(long id) {
        super(id);
    }
}
