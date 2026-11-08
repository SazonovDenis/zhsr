package kz.zhsr.app.dto.wagon;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Фильтр списка вагонов.
 *
 * @param depot         id депо приписки вагона
 * @param number        часть номера вагона
 * @param station       id станции: вагон на этой станции, в депо или в парке отстоя при ней
 * @param trip          id рейса, в составе которого вагон
 * @param locationKinds id вида расположения вагона (location_type)
 * @param states        id состояния вагона (wagon_status_type)
 * @param at            момент (например, отправление рейса): дополнительно к текущим - расположение и состояние на
 *                      этот момент по журналам; null - не нужны
 */
public record WagonFilter(Long depot, String number, Long station, Long trip, List<Long> locationKinds, List<Long> states,
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime at) {
}
