package kz.zhsr.app.dto.route;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Строка таблицы маршрутов: вариант выбора маршрута и поля таблицы. Все станции маршрута (точки) - только в карточке
 * маршрута ({@link Route}, GET /routes/{id}).
 */
@Getter
@Setter
public class RouteRow extends RouteOption {
    /** вид перевозки (transport_type) */
    private long transportType;
    /** дата начала действия */
    private LocalDate dbeg;
    /** дата окончания действия; null - маршрут действует */
    private LocalDate dend;
    /** норма часов за рейс для проводника */
    private Integer conductorHours;
    /** норма часов за рейс для ЛНП */
    private Integer lnpHours;
    /** норма часов за рейс для ПЭМ */
    private Integer pemHours;
}
