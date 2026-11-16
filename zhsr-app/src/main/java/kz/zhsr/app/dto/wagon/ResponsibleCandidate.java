package kz.zhsr.app.dto.wagon;

import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

/**
 * Кого можно назначить ответственным за вагоны: сотрудник и кто он - член бригады рейса поезда вагонов или сотрудник
 * депо станции.
 */
@Getter
@Setter
public class ResponsibleCandidate extends UserRef {
    /** должность в бригаде рейса (crew_duty_type); null - не в бригаде - вычисляемое */
    private Long crewDutyType;
    /** рейс, в бригаде которого сотрудник; null - не в бригаде - вычисляемое */
    private TripRef trip;
    /** сотрудник депо станции - вычисляемое */
    private boolean depotStaff;

    public ResponsibleCandidate() {
    }

    public ResponsibleCandidate(long id) {
        super(id);
    }

    /** Кандидат без подписи. */
    public static ResponsibleCandidate candidate(long usr) {
        return new ResponsibleCandidate(usr);
    }
}
