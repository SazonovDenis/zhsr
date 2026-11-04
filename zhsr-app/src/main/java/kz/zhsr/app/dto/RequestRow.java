package kz.zhsr.app.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Заявка на ремонт - строка списка и основа карточки ({@link RequestDetail}). Сотрудник и рейс - объектом: id и имя
 * для показа.
 */
@Getter
@Setter
public class RequestRow {
    private long id;
    /** номер: код компании, год, порядковый номер в году - «KZR-26-00042» */
    private String number;
    /** вагон (wagon) */
    private long wagon;
    /** номер вагона (wagon.number) */
    private String wagonNumber;
    /** тип вагона (wagon.wagon_type) */
    private long wagonType;
    /** вид расположения вагона сейчас (wagon_info.location_type); null - не задан */
    private Long locationType;
    /** место вагона по виду расположения - станция, депо или парк отстоя; null - вагон в рейсе */
    private Long place;
    /** рейс, в составе которого вагон едет сейчас; null - вагон не в рейсе */
    private Long wagonTrip;
    /** рейс вагона для показа - номер поезда и маршрут; null - вагон не в рейсе */
    private String wagonTripName;
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
    /** время подачи */
    private OffsetDateTime createdDt;
    private String description;
    /** статус (repair_request_info.request_status_type) */
    private long requestStatusType;
    /** текущий исполнитель (repair_request_info.executor_usr); null - не назначен */
    private UserRef executorUsr;
    /** вагон отцеплен в ТОР по этой заявке; пока не хранится - всегда false */
    private boolean tor;
    /** ПЭМ передал заявку в депо; пока не хранится - всегда false */
    private boolean forwarded;
    /** сколько фото приложено к заявке */
    private int photoCount;
}
