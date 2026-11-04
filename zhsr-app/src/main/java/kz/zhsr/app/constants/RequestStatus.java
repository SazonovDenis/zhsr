package kz.zhsr.app.constants;

/**
 * Статус заявки на ремонт: id request_status_type (db/dict/request_status_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/request_status_type.tsv - не править вручную.
 */
public final class RequestStatus {

    public static final String TABLE = "request_status_type";

    /** Новая - ждёт назначения исполнителя */
    public static final long NEW = 1;
    /** Назначена - исполнитель назначен */
    public static final long ASSIGNED = 2;
    /** В работе - исполнитель взял заявку в работу */
    public static final long IN_PROGRESS = 3;
    /** Выполнена - работа выполнена, ждёт приёмки */
    public static final long DONE = 4;
    /** Закрыта - работа принята */
    public static final long CLOSED = 5;
    /** Отклонена - необоснованна или не выполнена */
    public static final long REJECTED = 6;
    /** Отложена до ТО - решение мастера депо */
    public static final long DEFERRED = 7;
    /** Отменена - отменена */
    public static final long CANCELLED = 8;

    private RequestStatus() {
    }

}
