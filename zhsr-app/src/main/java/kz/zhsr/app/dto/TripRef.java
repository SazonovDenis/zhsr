package kz.zhsr.app.dto;

/**
 * Рейс в данных: id - для запросов, name - номер поезда и название маршрута для показа: «002 Алматы — Астана».
 */
public record TripRef(long id, String name) {
}
