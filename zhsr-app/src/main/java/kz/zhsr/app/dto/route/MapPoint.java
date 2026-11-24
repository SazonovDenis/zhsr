package kz.zhsr.app.dto.route;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Точка карты (stage_point, station_point): x - долгота, y - широта (WGS 84).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MapPoint {
    private double x;
    private double y;
}
