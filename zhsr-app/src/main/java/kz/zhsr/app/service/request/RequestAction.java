package kz.zhsr.app.service.request;

import kz.zhsr.app.constants.RequestStatus;

import java.util.Set;

/**
 * Действие над заявкой (Ф-ЗАЯ-03, Ф-ЗАЯ-07) и граф статусов заявки: из каких статусов действие выполняется, в какой
 * статус переводит и нужен ли комментарий. По графу {@link RequestRights} проверяет, доступно ли действие в текущем
 * статусе; кто может его выполнить - отдельные правила там же. Тот же граф - в zhsr-web (shared/requests/rights.ts,
 * ACTIONS, поле from). Порядок - порядок кнопок в zhsr-web.
 */
public enum RequestAction {

    /** Взять в работу */
    TAKE(RequestStatus.IN_PROGRESS, false, RequestStatus.NEW, RequestStatus.ASSIGNED, RequestStatus.DEFERRED),
    /** Выполнено; комментарий - что сделано */
    COMPLETE(RequestStatus.DONE, true, RequestStatus.IN_PROGRESS),
    /** Принять работу */
    ACCEPT(RequestStatus.CLOSED, false, RequestStatus.DONE),
    /** Вернуть на доработку */
    RETURN(RequestStatus.IN_PROGRESS, true, RequestStatus.DONE),
    /** Снять себя */
    RELEASE(RequestStatus.NEW, false, RequestStatus.IN_PROGRESS),
    /** Отложить до ТО: выполнить сейчас невозможно */
    DEFER(RequestStatus.DEFERRED, true, RequestStatus.NEW, RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS),
    /** Отклонить */
    REJECT(RequestStatus.REJECTED, true, RequestStatus.NEW, RequestStatus.ASSIGNED),
    /** Отменить (Ф-ЗАЯ-07) */
    CANCEL(RequestStatus.CANCELLED, true, RequestStatus.NEW, RequestStatus.ASSIGNED, RequestStatus.DEFERRED);

    /** статус после действия (request_status_type) */
    public final long status;
    /** комментарий обязателен */
    public final boolean commentRequired;
    /** статусы, из которых действие выполняется (request_status_type) */
    public final Set<Long> from;

    RequestAction(long status, boolean commentRequired, Long... from) {
        this.status = status;
        this.commentRequired = commentRequired;
        this.from = Set.of(from);
    }

}
