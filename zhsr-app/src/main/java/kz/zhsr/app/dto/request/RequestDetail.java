package kz.zhsr.app.dto.request;

import kz.zhsr.app.dto.ref.CheckTemplateRef;
import kz.zhsr.app.dto.ref.EquipTemplateRef;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Карточка заявки: поля заявки и плоские списки журналов, примечаний и фото. Как показать их историей - решает клиент.
 * Заявка создана Системой по нарушению в чек-листе или по недостаче в описи - источник: чек-лист или опись и шаблон.
 */
@Getter
@Setter
public class RequestDetail extends RequestRow {
    private List<RequestStatusRecord> statuses = List.of();
    private List<RequestExecutorRecord> executors = List.of();
    private List<RequestNote> notes = List.of();
    private List<RequestPhoto> photos = List.of();
    /** чек-лист, по нарушению в котором создана заявка; null - заявка не из чек-листа */
    private Long checkList;
    private CheckTemplateRef checkListTemplate;
    /** опись, по недостаче в которой создана заявка; null - заявка не из описи */
    private Long equipList;
    private EquipTemplateRef equipListTemplate;
}
