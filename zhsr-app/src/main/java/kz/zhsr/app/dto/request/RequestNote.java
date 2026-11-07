package kz.zhsr.app.dto.request;

import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Примечание к заявке (repair_request_note).
 */
@Getter
@Setter
public class RequestNote {
    private OffsetDateTime createdDt;
    private OffsetDateTime receivedDt;
    private UserRef createdByUsr;
    private String note;
}
