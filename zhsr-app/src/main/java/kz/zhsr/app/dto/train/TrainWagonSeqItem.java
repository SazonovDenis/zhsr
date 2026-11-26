package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.WagonRef;
import lombok.Getter;
import lombok.Setter;

/**
 * Позиция вагона в новом порядке - строка train_wagon_seq_item.
 */
@Getter
@Setter
public class TrainWagonSeqItem {
    private WagonRef wagon;
    /** позиция в поезде, с 1 */
    private int seq;
}
