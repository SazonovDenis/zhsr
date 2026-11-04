package kz.zhsr.app.dto;

import java.util.List;

/**
 * Пересчёт остановок в редакторе маршрута.
 *
 * @param stops   остановки по порядку
 * @param changed номера остановок (с 0), у которых сменилась станция или предыдущая остановка: им время в пути
 *                ставится по умолчанию
 */
public record RecalcStops(List<RouteStop> stops, List<Integer> changed) {
    public RecalcStops {
        stops = stops == null ? List.of() : List.copyOf(stops);
        changed = changed == null ? List.of() : List.copyOf(changed);
    }
}
