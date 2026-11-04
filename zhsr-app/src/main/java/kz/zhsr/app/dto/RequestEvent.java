package kz.zhsr.app.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Запись истории заявки: подача, смена статуса (журнал repair_request_status), назначение исполнителя (журнал
 * repair_request_executor), пояснение (repair_request_note).
 *
 * @param at       время
 * @param by       кто внёс запись; null - Система
 * @param kind     CREATED - подача, STATUS - смена статуса, ASSIGN - назначение исполнителя, NOTE - пояснение
 * @param action   STATUS: действие, которым сменился статус (RequestAction); иначе null
 * @param executor ASSIGN: новый исполнитель; null - назначение снято
 * @param text     комментарий к действию или текст пояснения; null - нет
 * @param photos   фото пояснения; пусто - нет
 */
public record RequestEvent(
        OffsetDateTime at,
        UserRef by,
        String kind,
        String action,
        UserRef executor,
        String text,
        List<RequestPhoto> photos
) {
    public RequestEvent {
        photos = photos == null ? List.of() : List.copyOf(photos);
    }
}
