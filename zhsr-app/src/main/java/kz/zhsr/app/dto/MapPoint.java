package kz.zhsr.app.dto;

/**
 * Точка линии перегона на карте (stage_point).
 *
 * @param id id точки (stage_point)
 * @param x  долгота (WGS 84)
 * @param y  широта (WGS 84)
 */
public record MapPoint(long id, double x, double y) {
}
