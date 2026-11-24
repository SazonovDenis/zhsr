package kz.zhsr.app.dto.route;

/**
 * Станция на карте: точка станции (station_point).
 *
 * @param station станция (station)
 * @param point   точка станции на карте
 */
public record StationPoint(long station, MapPoint point) {
}
