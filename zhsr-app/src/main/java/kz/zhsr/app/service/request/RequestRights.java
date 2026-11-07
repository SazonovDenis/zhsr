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
import java.util.EnumSet;
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
     * Действия, которые сотрудник может выполнить над заявкой сейчас.
     *
     * @param visible заявка видна сотруднику для исполнения (RequestSql, VISIBLE): такой сотрудник берёт её в работу
     *                и отклоняет
     */
    public Set<RequestAction> actions(CurrentUser user, RequestRow r, boolean visible) {
        Set<RequestAction> res = EnumSet.noneOf(RequestAction.class);
        long s = r.getRequestStatusType();
        if (FINAL_STATUSES.contains(s)) {
            return res;
        }
        boolean admin = isAdmin(user);
        boolean executor = isExecutor(user, r);
        boolean master = isMyMaster(user, r) || admin;
        // заявку, переданную в депо, бригада рейса уже не исполняет
        boolean work = visible && !(r.isForwarded() && inMyTrip(user, r));
        boolean pem = user.hasRole(RoleType.PEM) && inMyTrip(user, r) && user.trip().duty() == CrewDuty.PEM;
        boolean active = s == RequestStatus.NEW || s == RequestStatus.ASSIGNED || s == RequestStatus.IN_PROGRESS;

        // назначенную заявку берёт только назначенный исполнитель
        if ((s == RequestStatus.NEW || s == RequestStatus.DEFERRED) && (work || master)) {
            res.add(RequestAction.TAKE);
        }
        if (s == RequestStatus.ASSIGNED && (executor || admin)) {
            res.add(RequestAction.TAKE);
        }
        if (s == RequestStatus.IN_PROGRESS && (executor || admin)) {
            res.add(RequestAction.COMPLETE);
            res.add(RequestAction.NOT_DONE);
            res.add(RequestAction.RELEASE);
        }
        if (s == RequestStatus.DONE && canAccept(user, r)) {
            res.add(RequestAction.ACCEPT);
            res.add(RequestAction.RETURN);
        }
        if (active && ((pem && !r.isForwarded()) || admin)) {
            res.add(RequestAction.FORWARD);
        }
        if (active && master) {
            res.add(RequestAction.DEFER);
        }
        if ((s == RequestStatus.NEW || s == RequestStatus.ASSIGNED) && (work || master)) {
            res.add(RequestAction.REJECT);
        }
        if (master) {
            res.add(RequestAction.CANCEL);
        }
        if (active && master && !r.isTor()) {
            res.add(RequestAction.DETACH);
        }
        return res;
    }

    /**
     * Проверить, что сотрудник может выполнить действие.
     *
     * @param visible см. {@link #actions}
     * @throws ApiException 403 - не может
     */
    public void checkAction(CurrentUser user, RequestRow r, boolean visible, RequestAction action) {
        if (!actions(user, r, visible).contains(action)) {
            throw ApiException.forbidden("Действие недоступно");
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
        if (r.getRequestStatusType() != RequestStatus.NEW || r.getExecutorUsr() != null || r.isForwarded()
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
