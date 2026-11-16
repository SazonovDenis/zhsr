package kz.zhsr.app.dto.train;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Фильтр списка поездов.
 *
 * @param states состояние: active - действующие, closed - расформированные; пусто - все
 * @param search часть обозначения поезда или номера вагона поезда
 * @param from   варианты выбора для рейса: начало рейса, ISO с часовым поясом; null - занятость не считается
 * @param to     конец рейса
 * @param trip   рейс, для которого выбирается поезд: его самого занятость не учитывает; null - новый рейс
 */
public record TrainFilter(List<String> states, String search,
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
                          Long trip) {
}
