package kz.zhsr.app.dto.route;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Карточка маршрута: маршрут и все его станции по порядку.
 */
@Getter
@Setter
public class Route {
    private long id;
    private String trainNumber;
    private String name;
    /** вид маршрута (константы RouteKind) */
    private int routeKind;
    private long transportType;
    private LocalDate dbeg;
    /** null - маршрут действует */
    private LocalDate dend;
    /** время в пути, мин */
    private int duration;
    private Integer conductorHours;
    private Integer lnpHours;
    private Integer pemHours;
    private List<RoutePoint> points = List.of();
}
