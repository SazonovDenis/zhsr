package kz.zhsr.app.dto;

/**
 * Тело запроса действия над заявкой: комментарий; пусто - без комментария.
 */
public record RequestActionBody(String comment) {
}
