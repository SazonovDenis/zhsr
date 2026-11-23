package kz.zhsr.app.dto.analytics;

import java.util.List;

/**
 * Диаграмма «Количество и сроки исполнения заявок»: заявки по дням и сроки исполнения по неделям.
 *
 * @param days  дни по порядку без пропусков: с понедельника по сегодня
 * @param weeks недели этих дней по порядку, последняя не закончена
 */
public record RequestsChart(List<RequestsDay> days, List<RequestsWeek> weeks) {
}
