package kz.zhsr.app.dto.route;

import java.time.LocalDate;
import java.util.List;

/**
 * Сохранение маршрута из редактора (Ф-МАР-01, Ф-МАР-02): атрибуты и точки маршрута.
 *
 * @param trainNumber    номер поезда
 * @param name           сообщение; пусто - из названий остановок через тире
 * @param routeKind      вид маршрута (константы RouteKind)
 * @param dbeg           дата начала действия
 * @param dend           дата окончания действия; null - действует
 * @param conductorHours норма часов проводника
 * @param lnpHours       норма часов ЛНП
 * @param pemHours       норма часов ПЭМ
 * @param points         точки маршрута по порядку, как в редакторе: остановки и известные станции следования; между
 *                       соседними точками без перегона сервер прокладывает кратчайший путь. km точек и время станций
 *                       следования сервер считает сам; у остановок arrivalMin, departureMin - время прибытия и
 *                       отправления
 */
public record RouteSave(
        String trainNumber,
        String name,
        Integer routeKind,
        LocalDate dbeg,
        LocalDate dend,
        Integer conductorHours,
        Integer lnpHours,
        Integer pemHours,
        List<RoutePoint> points
) {
    public RouteSave {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
