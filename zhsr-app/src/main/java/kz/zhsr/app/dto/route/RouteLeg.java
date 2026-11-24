package kz.zhsr.app.dto.route;

/**
 * Пара соседних остановок в редакторе маршрута.
 *
 * @param from станция отправления (station)
 * @param to   станция прибытия (station)
 */
public record RouteLeg(long from, long to) {
}
