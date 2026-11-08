package kz.zhsr.app.dto.request;

import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.ref.WagonRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Заявка на ремонт - строка списка и основа карточки ({@link RequestDetail}). Сотрудник, рейс и вагон - ссылки ({@link kz.kis.kisdbtools.ref.Ref}).
 */
@Getter
@Setter
public class RequestRow {
    private long id;
    /** номер: код компании, год, порядковый номер в году - «KZR-26-00042» */
    private String number;
    private WagonRef wagon;
    /** вид расположения вагона сейчас (wagon_info.location_type); null - не задан */
    private Long locationType;
    /** место вагона по виду расположения - станция, депо или парк отстоя; null - вагон в рейсе */
    private Long place;
    /** рейс, в составе которого вагон едет сейчас; null - вагон не в рейсе */
    private TripRef wagonTrip;
    /** станция расположения вагона - станция, станция депо или парка отстоя; null - вагон в рейсе */
    private Long wagonStation;
    /** рейс заявки; null - без рейса */
    private TripRef trip;
    /** служба-исполнитель, определена типом неисправности (repair_request_info.service) */
    private long service;
    /** неисправность (fault_type) */
    private long faultType;
    /** критичность (severity_type) */
    private long severityType;
    /** автор заявки */
    private UserRef createdByUsr;
    /** время создания заявки пользователем */
    private OffsetDateTime createdDt;
    /** время поступления заявки на сервер */
    private OffsetDateTime receivedDt;
    private String description;
    /** статус (repair_request_info.request_status_type) */
    private long requestStatusType;
    /** текущий исполнитель (repair_request_info.executor_usr); null - не назначен */
    private UserRef executorUsr;
    /** сколько фото приложено к заявке */
    private int photoCount;
}
