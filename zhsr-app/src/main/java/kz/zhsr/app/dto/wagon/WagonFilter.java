package kz.zhsr.app.dto.wagon;

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
 */
public record WagonFilter(Long depot, String number, Long station, Long trip, List<Long> locationKinds, List<Long> states) {
}
