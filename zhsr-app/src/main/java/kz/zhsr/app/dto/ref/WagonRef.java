package kz.zhsr.app.dto.ref;

import kz.kis.kisdbtools.ref.Ref;
import lombok.Getter;
import lombok.Setter;

/**
 * Вагон в данных.
 */
@Getter
@Setter
public class WagonRef extends Ref {
    private String number;
    /** тип вагона */
    private long wagonType;

    public WagonRef() {
    }

    public WagonRef(long id) {
        super(id);
    }
}
