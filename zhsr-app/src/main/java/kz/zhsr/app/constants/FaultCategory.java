package kz.zhsr.app.constants;

/**
 * Категория неисправности: id fault_category_type (db/dict/fault_category_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/fault_category_type.tsv - не править вручную.
 */
public final class FaultCategory {

    public static final String TABLE = "fault_category_type";

    /** Техническая */
    public static final long TECHNICAL = 1;
    /** Уборка */
    public static final long CLEANING = 2;
    /** Экипировка */
    public static final long EQUIPMENT = 3;

    private FaultCategory() {
    }

}
