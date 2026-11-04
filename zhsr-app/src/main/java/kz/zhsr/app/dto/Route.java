package kz.zhsr.app.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Карточка маршрута (GET /routes/{id}): маршрут и все его станции по порядку.
 *
 * @param trainNumber    номер поезда
 * @param routeKind      вид маршрута (константы RouteKind)
 * @param transportType  вид перевозки (transport_type)
 * @param dbeg           дата начала действия
 * @param dend           дата окончания действия; null - маршрут действует
 * @param duration       время в пути, мин; вводит пользователь, сумма по станциям - подсказка в редакторе
 * @param conductorHours норма часов за рейс для проводника
 * @param lnpHours       норма часов за рейс для ЛНП
 * @param pemHours       норма часов за рейс для ПЭМ
 * @param points         все станции маршрута по порядку (route_station по seq): остановки и станции следования
 */
public record Route(
        long id,
        String trainNumber,
        String name,
        int routeKind,
        long transportType,
        LocalDate dbeg,
        LocalDate dend,
        int duration,
        Integer conductorHours,
        Integer lnpHours,
        Integer pemHours,
        List<RoutePoint> points
) {
    public Route {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
