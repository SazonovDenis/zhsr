package kz.zhsr.app.dto.route;

/**
 * Станция следования между соседними остановками.
 *
 * @param station станция (station)
 * @param km      расстояние от предыдущей остановки, км
 */
public record LegStation(long station, int km) {
}
