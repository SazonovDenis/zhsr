package kz.zhsr.app.dto.analytics;

import java.time.LocalDate;

/**
 * День диаграммы «Количество и сроки исполнения заявок».
 *
 * @param day      день
 * @param requests число заявок, поданных за день
 * @param trips    число рейсов, отправленных за день
 */
public record RequestsDay(LocalDate day, int requests, int trips) {
}
