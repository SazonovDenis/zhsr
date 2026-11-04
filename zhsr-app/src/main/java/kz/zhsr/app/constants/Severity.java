package kz.zhsr.app.constants;

/**
 * Критичность неисправности: id severity_type (db/dict/severity_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/severity_type.tsv - не править вручную.
 */
public final class Severity {

    public static final String TABLE = "severity_type";

    /** Низкая */
    public static final long LOW = 1;
    /** Средняя - по умолчанию */
    public static final long MEDIUM = 2;
    /** Высокая */
    public static final long HIGH = 3;
    /** Критическая */
    public static final long CRITICAL = 4;

    private Severity() {
    }

}
