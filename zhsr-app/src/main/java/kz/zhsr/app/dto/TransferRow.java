package kz.zhsr.app.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Акт приёма-передачи вагонов.
 *
 * @param transferDirectionType направление (transfer_direction_type)
 * @param station       станция передачи (station)
 * @param depot         депо; null - передача в парк отстоя или из него
 * @param yard          парк отстоя; null - передача в депо или из него
 * @param workType      вид работ (work_type)
 * @param actStatusType состояние акта (act_status_type)
 * @param transferredByUsr кто сдал: id и фамилия с инициалами
 * @param transferredDt когда сдал
 * @param receivedByUsr кто принял или отказал; null - приём не отмечен
 * @param receivedDt    когда принял или отказал; null - приём не отмечен
 * @param rejectReason  причина отказа; null - отказа нет
 * @param wagons        вагоны акта
 * @param canReceive    текущий сотрудник может принять вагоны или отказать
 */
public record TransferRow(long id, String number, long transferDirectionType, long station, Long depot, Long yard,
                          long workType, long actStatusType, UserRef transferredByUsr, OffsetDateTime transferredDt,
                          UserRef receivedByUsr, OffsetDateTime receivedDt, String rejectReason, List<Wagon> wagons,
                          boolean canReceive) {

    public TransferRow {
        wagons = wagons == null ? List.of() : List.copyOf(wagons);
    }

    /**
     * Вагон акта.
     *
     * @param withProperty передаётся с имуществом
     */
    public record Wagon(String number, boolean withProperty) {
    }

}
