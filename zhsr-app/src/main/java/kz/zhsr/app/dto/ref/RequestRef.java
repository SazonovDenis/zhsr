package kz.zhsr.app.dto.ref;

import kz.kis.kisdbtools.ref.Ref;
import lombok.Getter;
import lombok.Setter;

/**
 * Заявка на ремонт в данных: номер.
 */
@Getter
@Setter
public class RequestRef extends Ref {
    /** номер заявки: «KZR-26-00042» */
    private String number;

    public RequestRef() {
    }

    public RequestRef(long id) {
        super(id);
    }
}
