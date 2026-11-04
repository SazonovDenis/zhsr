package kz.zhsr.app.dto;

/**
 * Вариант для поля-ссылки в форме администрирования.
 *
 * @param id   id записи
 * @param text текст для показа
 */
public record AdminOption(long id, String text) {
}
