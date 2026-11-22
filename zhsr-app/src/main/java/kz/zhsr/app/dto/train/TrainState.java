package kz.zhsr.app.dto.train;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Запись участия вагона в поезде (train_wagon): позиция вагона с периодом действия, слой - факт или ожидаемый состав.
 */
@Getter
@Setter
public class TrainState {
    private long id;
    /** вагон (wagon.id) */
    private long wagon;
    /** слой: константы TrainWagonKind */
    private int kind;
    /** позиция в поезде, с 1 */
    private int seq;
    private OffsetDateTime dbeg;
    /** null - запись открыта */
    private OffsetDateTime dend;
}
