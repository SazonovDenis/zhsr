package kz.zhsr.app.dto.request;

import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Запись журнала исполнителей заявки (repair_request_executor).
 */
@Getter
@Setter
public class RequestExecutorRecord {
    private OffsetDateTime dbeg;
    /** исполнитель; null - назначение снято */
    private UserRef executorUsr;
    /** кто внёс запись; null - Система */
    private UserRef createdByUsr;
}
