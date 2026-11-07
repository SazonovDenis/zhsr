package kz.zhsr.app.dto.request;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Фильтр списка заявок (Ф-ЗАЯ-06).
 *
 * @param scope      mine - поданные сотрудником; todo - видные ему по правилам исполнения или назначенные лично;
 *                   all или пусто - все
 * @param closed     true - только в конечном статусе, false - только открытые, null - все
 * @param query      поиск по номеру заявки, номеру вагона и названию неисправности
 * @param statuses   статусы (request_status_type)
 * @param severities критичность (severity_type)
 * @param services   службы (service)
 * @param trip       рейс заявки
 * @param from       время подачи - с этой даты включительно; null - без ограничения
 * @param to         время подачи - по эту дату включительно; null - без ограничения
 */
public record RequestFilter(
        String scope,
        Boolean closed,
        String query,
        List<Long> statuses,
        List<Long> severities,
        List<Long> services,
        Long trip,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
) {
}
