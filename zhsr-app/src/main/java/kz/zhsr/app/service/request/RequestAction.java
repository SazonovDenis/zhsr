package kz.zhsr.app.service.request;

import kz.zhsr.app.constants.RequestStatus;

/**
 * Действие над заявкой (Ф-ЗАЯ-03, Ф-ЗАЯ-07): статус, в который оно переводит заявку, и нужен ли комментарий.
 * Порядок - порядок кнопок в zhsr-web (shared/requests/rights.ts, ACTIONS).
 */
public enum RequestAction {

    /** Взять в работу */
    TAKE(RequestStatus.IN_PROGRESS, false),
    /** Выполнено; комментарий - что сделано */
    COMPLETE(RequestStatus.DONE, true),
    /** Принять работу */
    ACCEPT(RequestStatus.CLOSED, false),
    /** Вернуть на доработку */
    RETURN(RequestStatus.IN_PROGRESS, true),
    /** Не выполнено */
    NOT_DONE(RequestStatus.REJECTED, true),
    /** Снять себя */
    RELEASE(RequestStatus.NEW, false),
    /** Передать в депо */
    FORWARD(RequestStatus.NEW, true),
    /** Отложить до ТО */
    DEFER(RequestStatus.DEFERRED, true),
    /** Отклонить */
    REJECT(RequestStatus.REJECTED, true),
    /** Отменить (Ф-ЗАЯ-07) */
    CANCEL(RequestStatus.CANCELLED, true),
    /** ТОР - отцепить вагон */
    DETACH(RequestStatus.IN_PROGRESS, true);

    /** статус после действия (request_status_type) */
    public final long status;
    /** комментарий обязателен */
    public final boolean commentRequired;

    RequestAction(long status, boolean commentRequired) {
        this.status = status;
        this.commentRequired = commentRequired;
    }

}
