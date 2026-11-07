package kz.zhsr.app.dto.wagon;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Строка таблицы вагонов: вариант выбора вагона и поля таблицы.
 */
@Getter
@Setter
public class WagonRow extends WagonOption {
    /** модель вагона (model_type); null - не задана */
    private Long modelType;
    /** депо приписки (depot); null - не задано */
    private Long depot;
    /** с какого времени вагон в этом расположении */
    private OffsetDateTime dbegLocation;
    /** состояние вагона (wagon_status_type) */
    private long wagonStatusType;
    /** с какого времени вагон в этом состоянии */
    private OffsetDateTime dbegStatus;
}
