package kz.zhsr.app.dto.staff;

import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.WagonRef;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Сотрудник в списке «Сотрудники»: роли, текущие депо и категория, где сотрудник сейчас (факт) и на какой рейс
 * назначен (план).
 */
@Getter
@Setter
public class StaffRow {
    private long id;
    private String fullName;
    /** логин; виден только администратору, остальным - null */
    private String login;
    private boolean disabled;
    /** роли сотрудника, id role_type по возрастанию; пусто - ролей нет - вычисляемое */
    private List<Long> roles = List.of();
    /** текущее депо (usr_depot); null - депо нет */
    private Long depot;
    /** станция текущего депо; null - депо нет или у депо нет станции */
    private Long station;
    /** текущая категория проводника (usr_category); null - категории нет */
    private Long categoryType;
    /** рейс в статусе «выполняется», в бригаде которого сотрудник; null - сотрудник не в рейсе - вычисляемое */
    private TripRef factTrip;
    /** должность в бригаде рейса factTrip (crew_duty_type); null - сотрудник не в рейсе - вычисляемое */
    private Long factDuty;
    /** вагоны, за которые сотрудник отвечает сейчас (текущий факт журнала ответственных), по номеру - вычисляемое */
    private List<WagonRef> factWagons = List.of();
    /** ближайший рейс в статусе «план», в бригаде которого сотрудник; null - не назначен - вычисляемое */
    private TripRef planTrip;
    /** должность в бригаде рейса planTrip (crew_duty_type); null - не назначен - вычисляемое */
    private Long planDuty;
    /** вагоны рейса planTrip по плану ответственных на рейс, по номеру - вычисляемое */
    private List<WagonRef> planWagons = List.of();
}
