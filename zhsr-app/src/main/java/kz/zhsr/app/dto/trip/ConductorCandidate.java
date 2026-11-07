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
    /** рейс, из которого проводник освободится к отправлению; null - свободен */
    private TripRef busyTrip;
    /** когда этот рейс прибудет */
    private OffsetDateTime busyUntil;
}
