package kz.zhsr.app.dto.route;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Станция маршрута (route_station); в теле сохранения маршрута - точка, введённая пользователем.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoutePoint {
    private long station;
    /** километраж от начала маршрута */
    private int km;
    /** остановка; иначе станция следования */
    private boolean haveStop;
    /** прибытие: минуты от полуночи дня отправления с начальной станции; null - у начальной станции */
    private Integer arrivalMin;
    /** отправление: минуты от полуночи дня отправления с начальной станции; null - у конечной станции */
    private Integer departureMin;
}
