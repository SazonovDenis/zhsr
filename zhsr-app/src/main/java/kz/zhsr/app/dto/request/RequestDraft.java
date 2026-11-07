package kz.zhsr.app.dto.request;

import java.time.OffsetDateTime;

/**
 * Новая или исправленная заявка (Ф-ЗАЯ-01, Ф-ЗАЯ-07); фото - отдельными частями запроса.
 *
 * @param wagon        вагон (wagon)
 * @param trip         рейс; null - без рейса
 * @param faultType    неисправность (fault_type)
 * @param severityType критичность (severity_type); null - из неисправности
 * @param description  описание
 * @param executorUsr  исполнитель; задаёт только мастер депо при подаче, null - не назначать
 * @param clientDt     когда пользователь подал или исправил заявку, по часам телефона: заявка, поданная без связи,
 *                     поступает позже; null - время сервера
 * @param clientGuid   guid записи, присвоен телефоном: защита от повторной отправки; null - не проверяется
 */
public record RequestDraft(
        Long wagon,
        Long trip,
        Long faultType,
        Long severityType,
        String description,
        Long executorUsr,
        OffsetDateTime clientDt,
        String clientGuid
) {
}
