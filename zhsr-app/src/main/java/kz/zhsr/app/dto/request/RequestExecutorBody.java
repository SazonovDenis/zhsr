package kz.zhsr.app.dto.request;

/**
 * Тело запроса назначения исполнителя: исполнитель; null - снять назначение.
 */
public record RequestExecutorBody(Long executor) {
}
