package kz.zhsr.app.dto.request;

import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.ref.WagonRef;
import lombok.Getter;
import lombok.Setter;

/**
 * Кому можно назначить заявку: сотрудник службы заявки или, когда назначает ЛНП, сотрудник из бригады рейса
 * (trip_crew).
 */
@Getter
@Setter
public class StaffCandidate {
    private UserRef usr;
    /** должность в бригаде рейса; null - выбор не из бригады */
    private Long crewDutyType;
    /** вагон в бригаде рейса; null - нет */
    private WagonRef wagon;
    /** второй вагон в бригаде рейса - вычисляемое: из второй строки бригады сотрудника; null - нет */
    private WagonRef wagon2;
    /** сотрудник службы заявки: у него есть роль этой службы */
    private boolean inService;
}
