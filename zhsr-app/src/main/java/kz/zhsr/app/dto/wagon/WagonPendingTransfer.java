package kz.zhsr.app.dto.wagon;

import lombok.Getter;
import lombok.Setter;

/**
 * Акт приёма-передачи, который ждёт приёма по вагону: номер и куда передаётся вагон.
 */
@Getter
@Setter
public class WagonPendingTransfer {
    /** transfer_act.id */
    private long id;
    private String number;
    /** куда: ровно одно из toStation, toDepot, toYard */
    private Long toStation;
    private Long toDepot;
    private Long toYard;
}
