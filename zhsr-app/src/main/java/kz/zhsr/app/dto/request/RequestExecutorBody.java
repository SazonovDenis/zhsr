package kz.zhsr.app.dto.request;

import java.time.OffsetDateTime;

/**
 * Тело запроса назначения исполнителя.
 *
 * @param executor   исполнитель; null - снять назначение
 * @param clientDt   когда пользователь назначил, по часам телефона; null - сейчас
 * @param clientGuid guid назначения, присвоен телефоном: повтор уже принятого назначения ничего не меняет
 */
public record RequestExecutorBody(Long executor, OffsetDateTime clientDt, String clientGuid) {
}
