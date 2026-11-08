package kz.zhsr.app.service.request;

import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.constants.CrewDuty;
import kz.zhsr.app.constants.RequestStatus;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.dto.request.RequestRow;
import kz.zhsr.app.service.ApiException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Права сотрудника на заявку на ремонт (Ф-ЗАЯ-02, Ф-ЗАЯ-03, Ф-ЗАЯ-07) - все правила в одном месте. Решает по данным
 * сотрудника (роли, депо, текущий рейс и должность в нём) и полям заявки (автор, исполнитель, где вагон).
 * Администратор может любое действие.
 * <p>
 * Видна ли заявка сотруднику для исполнения (Ф-ЗАЯ-06), решает условие SQL (RequestSql, VISIBLE) - им же
 * отбирается список «на исполнение»; сюда приходит готовый ответ visible.
 * <p>
 * Те же правила для показа кнопок - в zhsr-web (shared/requests/rights.ts); окончательно решает этот класс.
 */
@Service
public class RequestRights {

    /**
     * Конечные статусы: действий по заявке нет.
     */
    public static final Set<Long> FINAL_STATUSES = Set.of(RequestStatus.CLOSED, RequestStatus.REJECTED,
            RequestStatus.CANCELLED);

    /**
     * Сколько после подачи автор может исправить заявку (Ф-ЗАЯ-07).
     */
    public static final Duration EDIT_PERIOD = Duration.ofMinutes(10);

    /**
     * Почему сотрудник не может выполнить действие: сначала граф статусов ({@link RequestAction#from}), затем кто
     * выполняет действие. Те же правила - в zhsr-web (shared/requests/rights.ts, actionBlockReason).
     *
     * @param visible заявка видна сотруднику для исполнения (RequestSql, VISIBLE): такой сотрудник берёт её в работу
     *                и отклоняет
     * @return причина; null - может
     */
    public String blockReason(CurrentUser user, RequestRow r, boolean visible, RequestAction action) {
        long s = r.getRequestStatusType();
        if (!action.from.contains(s)) {
            return "Действие недоступно в текущем статусе заявки";
        }
        boolean admin = isAdmin(user);
        boolean executor = isExecutor(user, r) || admin;
        boolean master = isMyMaster(user, r) || admin;
        boolean work = visible || master;
        return switch (action) {
            // назначенную заявку берёт только назначенный исполнитель
            case TAKE -> s == RequestStatus.ASSIGNED
                    ? (executor ? null : "Берёт в работу только назначенный исполнитель")
                    : (work ? null : "Берёт в работу сотрудник службы заявки, которому она видна, или мастер депо");
            case COMPLETE, RELEASE -> executor ? null : "Только исполнитель заявки";
            case ACCEPT, RETURN -> canAccept(user, r) ? null
                    : "Принимает работу: вагон в рейсе - ЛНП рейса или проводник вагона, иначе - инструктор поездных"
                    + " бригад или эксплуатационщик депо станции вагона";
            // выполнить невозможно - откладывает мастер депо или исполнитель заявки в работе
            case DEFER -> master || (executor && s == RequestStatus.IN_PROGRESS) ? null
                    : "Откладывает мастер депо станции вагона или исполнитель заявки в работе";
            case REJECT -> work ? null : "Отклоняет сотрудник службы заявки или мастер депо";
            case CANCEL -> isAuthor(user, r) || admin ? null : "Отменяет только автор заявки";
        };
    }

    /**
     * Проверить, что сотрудник может выполнить действие.
     *
     * @param visible см. {@link #blockReason}
     * @throws ApiException 403 - не может, с причиной
     */
    public void checkAction(CurrentUser user, RequestRow r, boolean visible, RequestAction action) {
        String reason = blockReason(user, r, visible, action);
        if (reason != null) {
            throw ApiException.forbidden(reason);
        }
    }

    /**
     * Назначить или сменить исполнителя может мастер депо станции вагона, пока заявка не в конечном статусе
     * (Ф-ЗАЯ-02).
     *
     * @throws ApiException 403 - не может
     */
    public void checkAssign(CurrentUser user, RequestRow r) {
        if (FINAL_STATUSES.contains(r.getRequestStatusType()) || !(isMyMaster(user, r) || isAdmin(user))) {
            throw ApiException.forbidden("Назначить исполнителя может мастер депо станции вагона");
        }
    }

    /**
     * Выбрать исполнителя при подаче заявки может мастер депо (Ф-ЗАЯ-02).
     *
     * @throws ApiException 403 - не может
     */
    public void checkAssignOnCreate(CurrentUser user) {
        if (!(user.hasRole(RoleType.MASTER) || isAdmin(user))) {
            throw ApiException.forbidden("Назначить исполнителя при подаче может мастер депо");
        }
    }

    /**
     * Исправить заявку может автор в течение {@link #EDIT_PERIOD} после подачи, пока не сменились исполнитель и
     * статус (Ф-ЗАЯ-07).
     *
     * @throws ApiException 403 - не может
     */
    public void checkEdit(CurrentUser user, RequestRow r, OffsetDateTime now) {
        if (r.getCreatedByUsr().getId() != user.id()) {
            throw ApiException.forbidden("Исправить может только автор заявки");
        }
        if (r.getRequestStatusType() != RequestStatus.NEW || r.getExecutorUsr() != null
                || now.isAfter(r.getCreatedDt().plus(EDIT_PERIOD))) {
            throw ApiException.forbidden(
                    "Исправить можно в течение 10 минут после подачи, пока не сменились исполнитель и статус");
        }
    }

    /**
     * Кто принимает работу (Ф-ЗАЯ-03): вагон в рейсе - проводник этого вагона и начальник поезда рейса; вагон не в
     * рейсе - инструктор поездных бригад и эксплуатационщик депо на станции вагона.
     */
    private boolean canAccept(CurrentUser user, RequestRow r) {
        if (isAdmin(user)) {
            return true;
        }
        if (inMyTrip(user, r)) {
            CurrentUser.Trip trip = user.trip();
            return trip.duty() == CrewDuty.LNP
                    || (trip.duty() == CrewDuty.CONDUCTOR && trip.wagons().contains(r.getWagon().getId()));
        }
        return (user.hasRole(RoleType.INSTRUCTOR) || user.hasRole(RoleType.OPERATOR)) && atMyDepot(user, r);
    }

    private static boolean isAdmin(CurrentUser user) {
        return user.hasRole(RoleType.ADMIN);
    }

    private static boolean isAuthor(CurrentUser user, RequestRow r) {
        return r.getCreatedByUsr() != null && r.getCreatedByUsr().getId() == user.id();
    }

    private static boolean isExecutor(CurrentUser user, RequestRow r) {
        return r.getExecutorUsr() != null && r.getExecutorUsr().getId() == user.id();
    }

    /**
     * Вагон заявки едет в текущем рейсе сотрудника.
     */
    private static boolean inMyTrip(CurrentUser user, RequestRow r) {
        return user.trip() != null && r.getWagonTrip() != null && r.getWagonTrip().getId() == user.trip().id();
    }

    /**
     * Вагон заявки не в рейсе и стоит на станции депо сотрудника.
     */
    private static boolean atMyDepot(CurrentUser user, RequestRow r) {
        return r.getWagonTrip() == null && r.getWagonStation() != null && r.getWagonStation().equals(user.depotStation());
    }

    /**
     * Мастер депо, которому подчиняется заявка: вагон на станции его депо.
     */
    private static boolean isMyMaster(CurrentUser user, RequestRow r) {
        return user.hasRole(RoleType.MASTER) && atMyDepot(user, r);
    }

}
