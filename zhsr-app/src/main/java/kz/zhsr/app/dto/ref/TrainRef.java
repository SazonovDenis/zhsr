package kz.zhsr.app.dto.ref;

import kz.kis.kisdbtools.ref.Ref;
import lombok.Getter;
import lombok.Setter;

/**
 * Поезд в данных.
 */
@Getter
@Setter
public class TrainRef extends Ref {
    /** обозначение поезда */
    private String name;

    public TrainRef() {
    }

    public TrainRef(long id) {
        super(id);
    }
}
