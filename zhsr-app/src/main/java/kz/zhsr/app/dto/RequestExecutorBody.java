package kz.zhsr.app.dto;

/**
 * Тело запроса назначения исполнителя: исполнитель; null - снять назначение.
 */
public record RequestExecutorBody(Long executor) {
}
