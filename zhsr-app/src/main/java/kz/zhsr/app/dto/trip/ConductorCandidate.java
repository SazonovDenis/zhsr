package kz.zhsr.app.dto.trip;

import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Проводник для набора в бригаду рейса: сотрудник-ссылка и его занятость.
 */
@Getter
@Setter
public class ConductorCandidate extends UserRef {
    /** старший проводник */
    private boolean senior;
    /** уже в бригаде этого рейса */
    private boolean assigned;
    /** занят в другом рейсе, пересекающемся по времени с этим: назначить его нельзя */
    private boolean busy;
    /** другой рейс проводника: пересекающийся с этим (busy), иначе тот, после которого он освободится; null - рейсов нет */
    private TripRef busyTrip;
    /** когда этот рейс прибудет */
    private OffsetDateTime busyUntil;
}
