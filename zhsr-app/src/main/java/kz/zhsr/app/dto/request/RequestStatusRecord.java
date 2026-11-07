package kz.zhsr.app.dto.request;

import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Запись журнала статусов заявки (repair_request_status).
 */
@Getter
@Setter
public class RequestStatusRecord {
    private OffsetDateTime dbeg;
    private long requestStatusType;
    /** кто внёс запись; null - Система */
    private UserRef createdByUsr;
}
