package kz.zhsr.app.constants;

/**
 * Категория проводника: id category_type (db/dict/category_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/category_type.tsv - не править вручную.
 */
public final class ConductorCategory {

    public static final String TABLE = "category_type";

    /** Стажёр - проходит стажировку под руководством проводника */
    public static final long TRAINEE = 1;
    /** Проводник 2 разряда - ЕТКС: проводник пассажирского вагона 2-го разряда */
    public static final long GRADE_2 = 2;
    /** Проводник 3 разряда - ЕТКС: проводник пассажирского вагона 3-го разряда (поезда дальнего следования) */
    public static final long GRADE_3 = 3;
    /** Старший проводник - вагоны повышенной категории, координирует проводников */
    public static final long SENIOR = 4;

    private ConductorCategory() {
    }

}
