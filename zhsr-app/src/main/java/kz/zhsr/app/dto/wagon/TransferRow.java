package kz.zhsr.app.dto.wagon;

import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Акт приёма-передачи.
 */
@Getter
@Setter
public class TransferRow {
    private long id;
    private String number;
    private Long fromStation;
    private Long fromDepot;
    private Long fromYard;
    private Long toStation;
    private Long toDepot;
    private Long toYard;
    /** станция акта - станция расположения «откуда» (вычисляемое) */
    private long station;
    private long workType;
    private long actStatusType;
    private UserRef transferredByUsr;
    private OffsetDateTime transferredDt;
    /** кто принял; null - акт не принят */
    private UserRef receivedByUsr;
    private OffsetDateTime receivedDt;
    private String rejectReason;
    private List<Wagon> wagons = List.of();
    /** текущий сотрудник может принять или отклонить акт */
    private boolean canReceive;

    /**
     * Вагон акта.
     */
    @Getter
    @Setter
    public static class Wagon {
        private String number;
        private boolean withProperty;
    }
}
