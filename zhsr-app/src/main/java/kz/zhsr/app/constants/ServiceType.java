package kz.zhsr.app.constants;

/**
 * Служба: id service (db/dict/service.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/service.tsv - не править вручную.
 */
public final class ServiceType {

    public static final String TABLE = "service";

    /** Техническая - ПЭМ, мастер депо */
    public static final long TECHNICAL = 1;
    /** Клининг */
    public static final long CLEANING = 2;
    /** Экипировка */
    public static final long EQUIPMENT = 3;
    /** Инструкторская - инструктор поездных бригад, инструктор-нормировщик, медик */
    public static final long INSTRUCTOR = 4;
    /** Эксплуатация - эксплуатационщик */
    public static final long OPERATION = 5;

    private ServiceType() {
    }

}
