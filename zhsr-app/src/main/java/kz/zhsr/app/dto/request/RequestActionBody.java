package kz.zhsr.app.dto.request;

import java.time.OffsetDateTime;

/**
 * Тело запроса действия над заявкой.
 *
 * @param comment    комментарий; пусто - без комментария
 * @param clientDt   когда пользователь выполнил действие, по часам телефона: без связи действие ждёт на телефоне;
 *                   null - сейчас
 * @param clientGuid guid действия, присвоен телефоном: повтор уже принятого действия ничего не меняет
 */
public record RequestActionBody(String comment, OffsetDateTime clientDt, String clientGuid) {
}
