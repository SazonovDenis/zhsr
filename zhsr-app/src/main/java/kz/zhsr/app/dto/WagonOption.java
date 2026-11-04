package kz.zhsr.app.dto;

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
    /** вид расположения (location_type) */
    private long locationType;
    /** место расположения по виду: станция (station), депо (depot), парк отстоя (yard) или рейс (trip) */
    private long place;
    /** номер поезда рейса, в составе которого вагон; null - вагон не в рейсе */
    private String trainNumber;
    /** маршрут рейса, в составе которого вагон; null - вагон не в рейсе */
    private String name;
}
