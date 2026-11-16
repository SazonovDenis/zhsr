package kz.zhsr.app.service.check;

import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.constants.CheckObject;
import kz.zhsr.app.constants.CrewDuty;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.service.ApiException;

import java.util.List;
import java.util.Map;

/**
 * Права на чек-листы и описи экипировки (tz.actors.md): кто проводит проверку по объекту проверки, кто составляет
 * опись, кто видит все проверки и описи. Администратор может всё. Правится здесь; фронт держит ту же матрицу
 * (zhsr-web shared/checks/rights.ts).
 */
public final class CheckRights {

    /**
     * Объект проверки (константы CheckObject) - роли, которые проводят проверку (Ф-ЧЕК-03).
     */
    public static final Map<Integer, List<Long>> CHECK_BY_OBJECT = Map.of(
            CheckObject.WAGON, List.of(RoleType.CONDUCTOR, RoleType.LNP, RoleType.PEM, RoleType.MASTER,
                    RoleType.RECEIVER, RoleType.INSTRUCTOR),
            CheckObject.USR, List.of(RoleType.MEDIC, RoleType.INSTRUCTOR),
            CheckObject.TRIP, List.of(RoleType.LNP, RoleType.INSTRUCTOR)
    );

    /**
     * Роли, которые составляют опись экипировки (Ф-ЭКП-02).
     */
    public static final List<Long> EQUIP = List.of(RoleType.EQUIPPER, RoleType.CONDUCTOR, RoleType.LNP,
            RoleType.MASTER, RoleType.RECEIVER, RoleType.INSTRUCTOR);

    /**
     * Роли, которые видят все проверки и описи (Ф-ЧЕК-04, Ф-ЭКП-03); остальные видят свои.
     */
    public static final List<Long> SEE_ALL = List.of(RoleType.MANAGER, RoleType.INSTRUCTOR, RoleType.MASTER,
            RoleType.RECEIVER);

    private CheckRights() {
    }

    /**
     * @throws ApiException 403 - сотрудник не проводит проверку такого объекта
     */
    public static void checkCheck(CurrentUser user, int checkObject) {
        if (!hasAny(user, CHECK_BY_OBJECT.getOrDefault(checkObject, List.of()))) {
            throw ApiException.forbidden("Вы не проводите проверку по этому чек-листу");
        }
    }

    /**
     * @throws ApiException 403 - сотрудник не составляет опись экипировки
     */
    public static void checkEquip(CurrentUser user) {
        if (!hasAny(user, EQUIP)) {
            throw ApiException.forbidden("Вы не составляете опись экипировки");
        }
    }

    /**
     * Сотрудник видит все проверки и описи, а не только свои.
     */
    public static boolean seesAll(CurrentUser user) {
        return hasAny(user, SEE_ALL);
    }

    /**
     * Что, кроме проведённых им самим, сотрудник видит в списках проверок и описей (Ф-ЧЕК-04, Ф-ЭКП-03), если не видит
     * все ({@link #seesAll}): проводник - по своим вагонам текущего рейса, ЛНП и ПЭМ - по вагонам состава своего
     * рейса и по самому рейсу.
     *
     * @param wagons вагоны проводника; пусто - нет
     * @param trip   рейс ЛНП или ПЭМ; null - нет
     */
    public record ListScope(List<Long> wagons, Long trip) {
        public ListScope {
            wagons = wagons == null ? List.of() : List.copyOf(wagons);
        }
    }

    public static ListScope scope(CurrentUser user) {
        CurrentUser.Trip trip = user.trip();
        if (trip == null) {
            return new ListScope(List.of(), null);
        }
        if (trip.duty() == CrewDuty.CONDUCTOR) {
            return new ListScope(trip.wagons(), null);
        }
        return trip.duty() == CrewDuty.LNP || trip.duty() == CrewDuty.PEM
                ? new ListScope(List.of(), trip.id())
                : new ListScope(List.of(), null);
    }

    private static boolean hasAny(CurrentUser user, List<Long> roles) {
        return user.hasRole(RoleType.ADMIN) || roles.stream().anyMatch(user::hasRole);
    }

}
