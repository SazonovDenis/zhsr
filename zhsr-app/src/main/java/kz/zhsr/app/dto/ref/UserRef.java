package kz.zhsr.app.dto.ref;

import kz.kis.kisdbtools.ref.Ref;
import lombok.Getter;
import lombok.Setter;

/**
 * Сотрудник в данных.
 */
@Getter
@Setter
public class UserRef extends Ref {
    /** фамилия и инициалы */
    private String name;

    public UserRef() {
    }

    public UserRef(long id) {
        super(id);
    }
}
