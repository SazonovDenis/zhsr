package kz.zhsr.app.dto.train;

import lombok.Getter;
import lombok.Setter;

/**
 * Вагон в строке поезда (TrainRow): поля вагона для колонок «Номер» и «Тип» списка вагонов.
 */
@Getter
@Setter
public class TrainRowWagon {
    private long id;
    private String number;
    /** тип вагона */
    private long wagonType;
    /** модель вагона; null - не задана */
    private Long modelType;
}
