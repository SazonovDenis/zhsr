package kz.zhsr.app.constants;

/**
 * Состояние вагона: id wagon_status_type (db/dict/wagon_status_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/wagon_status_type.tsv - не править вручную.
 */
public final class WagonStatus {

    public static final String TABLE = "wagon_status_type";

    /** В простое - по умолчанию */
    public static final long IN_IDLE = 1;
    /** В пути */
    public static final long IN_TRIP = 2;
    /** В ремонте - ставится при ТОР */
    public static final long IN_REPAIR = 3;
    /** Резерв - подбирается в состав, если не хватает годных */
    public static final long RESERVE = 4;
    /** Отставлен */
    public static final long OUT_OF_SERVICE = 5;

    private WagonStatus() {
    }

}
