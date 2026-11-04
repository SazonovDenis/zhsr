package kz.zhsr.app.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Карточка заявки: поля заявки, история и фото.
 */
@Getter
@Setter
public class RequestDetail extends RequestRow {
    /** история по времени, от старых к новым */
    private List<RequestEvent> events = List.of();
    /** фото, приложенные при подаче */
    private List<RequestPhoto> photos = List.of();
}
