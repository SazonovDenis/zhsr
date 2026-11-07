package kz.zhsr.app.dto.wagon;

import lombok.Getter;
import lombok.Setter;

/**
 * Вагон для акта приёма-передачи.
 */
@Getter
@Setter
public class TransferWagon {
    private String number;
    private long wagonType;
    private long locationType;
    private long place;
    private long wagonStatusType;
    /** номер акта, ожидающего приёма, по этому вагону; null - такого акта нет */
    private String pendingAct;
}
