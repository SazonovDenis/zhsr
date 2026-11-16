package kz.zhsr.app.dto.request;

/**
 * Тело запроса исправления примечания к заявке.
 *
 * @param text новый текст примечания
 */
public record RequestNoteBody(String text) {
}
