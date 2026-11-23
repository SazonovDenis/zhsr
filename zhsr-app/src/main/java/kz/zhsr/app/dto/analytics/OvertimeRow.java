package kz.zhsr.app.dto.analytics;

import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Строка диаграммы «переработка»: сотрудник и его рабочие часы по месяцам.
 */
@Getter
@Setter
public class OvertimeRow {
    /** сотрудник */
    private UserRef usr;
    /** обязанность в бригаде (CrewDuty): проводник или ЛНП */
    private long crewDutyType;
    /** текущее депо сотрудника (справочник Depot); null - депо нет */
    private Long depot;
    /** часы по месяцам, в порядке OvertimeChart.months */
    private List<Integer> hours;
}
