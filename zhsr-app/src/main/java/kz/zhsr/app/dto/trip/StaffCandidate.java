package kz.zhsr.app.dto.trip;

import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Сотрудник для должности начальника поезда или ПЭМ: сотрудник-ссылка и его занятость. Занятых не прячем: они видны
 * с пометкой, а назначить их не даёт сервер.
 */
@Getter
@Setter
public class StaffCandidate extends UserRef {
    /** рейс, на котором сотрудник занят во время этого рейса; null - свободен */
    private TripRef busyTrip;
    /** до какого времени занят: фактическое прибытие того рейса, а если его нет - плановое */
    private OffsetDateTime busyUntil;
}
