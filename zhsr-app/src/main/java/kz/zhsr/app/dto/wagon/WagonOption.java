package kz.zhsr.app.dto.wagon;

import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

/**
 * Вагон как вариант выбора (подбор вагона в заявке): вагон, тип и где он сейчас. Строка таблицы вагонов
 * ({@link WagonRow}) - вариант выбора и поля таблицы.
 */
@Getter
@Setter
public class WagonOption {
    private long id;
    private String number;
    /** тип вагона (wagon_type) */
    private long wagonType;
    /** вид расположения (location_type); null - расположение не задано */
    private Long locationType;
    /** место расположения по виду: станция (station), депо (depot), парк отстоя (yard) или рейс (trip); null - не задано */
    private Long place;
    /** рейс, в составе которого вагон; null - вагон не в рейсе */
    private TripRef trip;
}
