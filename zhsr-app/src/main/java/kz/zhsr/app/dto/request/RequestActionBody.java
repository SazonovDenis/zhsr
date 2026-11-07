package kz.zhsr.app.dto.request;

/**
 * Тело запроса действия над заявкой: комментарий; пусто - без комментария.
 */
public record RequestActionBody(String comment) {
}
