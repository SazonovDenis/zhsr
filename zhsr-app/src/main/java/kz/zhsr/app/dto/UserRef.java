package kz.zhsr.app.dto;

/**
 * Сотрудник в данных: id - для запросов, name - фамилия и инициалы для показа.
 */
public record UserRef(long id, String name) {
}
