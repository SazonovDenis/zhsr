package kz.zhsr.app.dto.analytics;

import lombok.Getter;
import lombok.Setter;

/**
 * Строка диаграммы «Заявки»: выполненные заявки одной службы, неисправности и депо исполнителя.
 */
@Getter
@Setter
public class WorkTimeRow {
    /** служба (ServiceType) */
    private Long service;
    /** неисправность (справочник FaultType) */
    private Long faultType;
    /** текущее депо исполнителя (справочник Depot); null - исполнителя или депо нет */
    private Long depot;
    /** число выполненных заявок */
    private int requests;
    /** минуты от взятия в работу до первого выполнения, сумма по заявкам */
    private long minutes;
}
