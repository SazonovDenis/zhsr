package kz.zhsr.app.dto.trip;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Фильтр списка рейсов.
 *
 * @param id      id рейса: только этот рейс
 * @param depot   id депо: маршрут рейса идёт через станцию этого депо
 * @param route   id маршрута
 * @param station id станции, через которую идёт маршрут рейса
 * @param from    начало периода, включительно: рейс не закончился к этому времени (плановое отправление плюс длительность
 *                не раньше from) или ещё выполняется; ISO с часовым поясом
 * @param to      конец периода планового отправления, не включительно: ISO с часовым поясом
 * @param states  id состояния рейса (trip_status_type)
 */
public record TripFilter(Long id, Long depot, Long route, Long station,
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
                         List<Long> states) {
}
