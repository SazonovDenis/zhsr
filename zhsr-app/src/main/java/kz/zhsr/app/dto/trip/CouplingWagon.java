package kz.zhsr.app.dto.trip;

import kz.zhsr.app.dto.train.WagonBusy;
import lombok.Getter;
import lombok.Setter;

/**
 * Вагон для прицепки или отцепки в рейсе.
 */
@Getter
@Setter
public class CouplingWagon {
    private String number;
    /** отцепка: позиция вагона в поезде; прицепка - null */
    private Integer seq;
    private long wagonType;
    private long locationType;
    private long place;
    private long wagonStatusType;
    /** занятость вагона в другом поезде; null - вагон свободен */
    private WagonBusy busy;
    /** можно отцепить или прицепить; иначе показан, но отметить нельзя */
    private boolean available;
}
