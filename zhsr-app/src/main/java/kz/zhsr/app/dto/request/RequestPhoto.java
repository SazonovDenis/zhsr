package kz.zhsr.app.dto.request;

import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Фото заявки (repair_request_photo); файл - GET /requests/photos/{id}.
 */
@Getter
@Setter
public class RequestPhoto {
    private long id;
    private OffsetDateTime createdDt;
    private OffsetDateTime receivedDt;
    private UserRef createdByUsr;
}
