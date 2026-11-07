package kz.zhsr.app.dto.route;

import lombok.Getter;
import lombok.Setter;

/**
 * Точка карты (stage_point).
 */
@Getter
@Setter
public class MapPoint {
    private long id;
    private double x;
    private double y;
}
