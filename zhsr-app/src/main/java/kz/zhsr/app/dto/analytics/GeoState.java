package kz.zhsr.app.dto.analytics;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Участие вагона в поезде (train_wagon).
 */
@Getter
@Setter
public class GeoState {
    private long train;
    private long wagon;
    /** слой - константы TrainWagonKind */
    private int kind;
    private OffsetDateTime dbeg;
    /** null - запись открыта */
    private OffsetDateTime dend;
}
