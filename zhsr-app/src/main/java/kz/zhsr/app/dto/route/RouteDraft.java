package kz.zhsr.app.dto.route;

import java.time.LocalDate;

/**
 * Новый маршрут (Ф-МАР-01): атрибуты из диалога «Новый маршрут»; остановки вводятся в редакторе. Поля - по именам
 * колонок route.
 *
 * @param trainNumber номер поезда
 * @param name        сообщение: «Астана — Алматы»
 * @param routeKind   вид маршрута (константы RouteKind)
 * @param dbeg        дата начала действия
 */
public record RouteDraft(String trainNumber, String name, Integer routeKind, LocalDate dbeg) {
}
