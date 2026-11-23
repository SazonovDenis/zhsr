package kz.zhsr.app.dto.analytics;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Диаграмма «переработка»: рабочие часы бригад по месяцам относительно нормы.
 *
 * @param months начала месяцев по порядку: 11 полных месяцев и текущий, незаконченный
 * @param norm   норма часов в месяц
 * @param rows   сотрудники с часами; пустой список - рейсов за период нет
 */
public record OvertimeChart(List<OffsetDateTime> months, int norm, List<OvertimeRow> rows) {
}
