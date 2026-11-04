package kz.zhsr.app.constants;

/**
 * Должность члена бригады на рейсе: id crew_duty_type (db/dict/crew_duty_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/crew_duty_type.tsv - не править вручную.
 */
public final class CrewDuty {

    public static final String TABLE = "crew_duty_type";

    /** Проводник - может быть закреплён за вагоном */
    public static final long CONDUCTOR = 1;
    /** Начальник поезда */
    public static final long LNP = 2;
    /** Поездной электромеханик */
    public static final long PEM = 3;

    private CrewDuty() {
    }

}
