package kz.zhsr.app.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Карточка заявки: поля заявки и плоские списки журналов, примечаний и фото. Как показать их историей - решает клиент.
 */
@Getter
@Setter
public class RequestDetail extends RequestRow {
    private List<RequestStatusRecord> statuses = List.of();
    private List<RequestExecutorRecord> executors = List.of();
    private List<RequestNote> notes = List.of();
    private List<RequestPhoto> photos = List.of();
}
