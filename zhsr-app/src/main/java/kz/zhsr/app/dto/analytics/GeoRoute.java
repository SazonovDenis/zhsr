package kz.zhsr.app.dto.analytics;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Маршрут на карте.
 */
@Getter
@Setter
public class GeoRoute {
    private long id;
    /** номер поезда */
    private String trainNumber;
    private String name;
    /** точки линии по порядку: долгота, широта, километр от начала линии */
    private List<double[]> points = List.of();
}
