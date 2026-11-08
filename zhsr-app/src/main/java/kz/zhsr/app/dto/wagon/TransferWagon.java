package kz.zhsr.app.dto.wagon;

import lombok.Getter;
import lombok.Setter;

/**
 * Вагон для акта приёма-передачи: строка таблицы вагонов и акт, который ждёт приёма по этому вагону.
 */
@Getter
@Setter
public class TransferWagon extends WagonRow {
    /** номер акта, ожидающего приёма, по этому вагону; null - такого акта нет */
    private String pendingAct;
    /** в акте, ожидающем приёма, вагон передаётся с имуществом; null - такого акта нет */
    private Boolean pendingWithProperty;
}
