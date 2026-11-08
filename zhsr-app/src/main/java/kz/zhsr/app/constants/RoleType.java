package kz.zhsr.app.constants;

/**
 * Роль сотрудника: id role_type (db/dict/role_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/role_type.tsv - не править вручную.
 */
public final class RoleType {

    public static final String TABLE = "role_type";

    /** Администратор компании */
    public static final long ADMIN = 1;
    /** Проводник */
    public static final long CONDUCTOR = 2;
    /** Поездной электромеханик (ПЭМ) */
    public static final long PEM = 3;
    /** Начальник поезда (ЛНП) */
    public static final long LNP = 4;
    /** Мастер депо */
    public static final long MASTER = 5;
    /** Приёмщик */
    public static final long RECEIVER = 6;
    /** Клининг - учётная запись подрядчика */
    public static final long CLEANING = 7;
    /** Руководитель */
    public static final long MANAGER = 8;
    /** Экипировщик - МСИ, съёмное оборудование, посуда, медицинские принадлежности */
    public static final long EQUIPPER = 9;
    /** Инструктор поездных бригад - замещает нормировщика */
    public static final long INSTRUCTOR = 10;
    /** Инструктор-нормировщик (нарядный стол) */
    public static final long SCHEDULER = 11;
    /** Планировщик вагонов - нет в zholserik; вагоны, маршруты, рейсы, состав, ЛНП и ПЭМ (tz.actors.md) */
    public static final long WAGON_PLANNER = 12;
    /** Эксплуатационщик */
    public static final long OPERATOR = 13;
    /** Планировщик проводников - нет в zholserik; проводники рейса (tz.actors.md) */
    public static final long CREW_PLANNER = 14;

    private RoleType() {
    }

}
