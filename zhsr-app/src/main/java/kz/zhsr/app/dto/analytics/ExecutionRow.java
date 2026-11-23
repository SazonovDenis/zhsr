package kz.zhsr.app.dto.analytics;

import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Строка диаграммы «Скорость и качество исполнения заявок»: исполнитель и его заявки.
 */
@Getter
@Setter
public class ExecutionRow {
    /** исполнитель */
    private UserRef executorUsr;
    /** текущее депо исполнителя (справочник Depot); null - депо нет */
    private Long depot;
    /** минуты от взятия в работу до первого выполнения, по заявкам; не пуст */
    private List<Long> minutes = new ArrayList<>();
    /** число этих заявок, возвращённых на доработку */
    private int returned;
}
