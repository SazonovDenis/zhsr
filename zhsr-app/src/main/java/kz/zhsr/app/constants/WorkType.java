package kz.zhsr.app.constants;

/**
 * Вид работ: id work_type (db/dict/work_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/work_type.tsv - не править вручную.
 */
public final class WorkType {

    public static final String TABLE = "work_type";

    /** ТО */
    public static final long TO = 1;
    /** ТОР */
    public static final long TOR = 2;
    /** ТР-1 */
    public static final long TR1 = 3;
    /** ТР-2 */
    public static final long TR2 = 4;
    /** ДР */
    public static final long DR = 5;
    /** КР */
    public static final long KR = 6;
    /** ЕТР */
    public static final long ETR = 7;
    /** Отстой - без ремонта */
    public static final long STORAGE = 8;

    private WorkType() {
    }

}
