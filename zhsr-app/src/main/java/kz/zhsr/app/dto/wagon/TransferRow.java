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
    private long transferDirectionType;
    private long station;
    private Long depot;
    private Long yard;
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
