package kz.zhsr.app.dto.route;

import java.util.List;

/**
 * Станция, на которую можно перейти от предыдущей остановки.
 *
 * @param station     станция (station)
 * @param km          расстояние от начала маршрута, км
 * @param distance    расстояние от предыдущей остановки, км
 * @param via         станции следования между предыдущей остановкой и этой станцией (station)
 * @param runDuration время в пути от предыдущей остановки по умолчанию, мин
 */
public record NextStation(long station, int km, int distance, List<Long> via, int runDuration) {
    public NextStation {
        via = via == null ? List.of() : List.copyOf(via);
    }
}
