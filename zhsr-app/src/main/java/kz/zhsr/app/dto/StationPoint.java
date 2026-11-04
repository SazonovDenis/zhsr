package kz.zhsr.app.dto;

/**
 * Станция на карте: точка станции (station.stage_point).
 *
 * @param station станция (station)
 * @param point   точка станции на карте
 */
public record StationPoint(long station, MapPoint point) {
}
