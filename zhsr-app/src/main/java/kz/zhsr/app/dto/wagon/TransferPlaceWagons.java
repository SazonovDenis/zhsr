package kz.zhsr.app.dto.wagon;

import lombok.Getter;
import lombok.Setter;

/**
 * Расположение станции и сколько вагонов в нём стоит: станция, депо или парк отстоя - заполнено одно из полей.
 */
@Getter
@Setter
public class TransferPlaceWagons {
    private Long station;
    private Long depot;
    private Long yard;
    /** сколько вагонов стоит в расположении (вычисляемое) */
    private int wagonCount;
}
