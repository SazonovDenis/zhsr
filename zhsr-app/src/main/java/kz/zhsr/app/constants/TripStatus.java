package kz.zhsr.app.constants;

/**
 * Состояние рейса: id trip_status_type (db/dict/trip_status_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/trip_status_type.tsv - не править вручную.
 */
public final class TripStatus {

    public static final String TABLE = "trip_status_type";

    /** План - по умолчанию */
    public static final long PLANNED = 1;
    /** Выполняется */
    public static final long IN_PROGRESS = 2;
    /** Выполнен */
    public static final long DONE = 3;
    /** Отменён */
    public static final long CANCELLED = 4;

    private TripStatus() {
    }

}
