package kz.zhsr.app.constants;

/**
 * Вид перевозки: id transport_type (db/dict/transport_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/transport_type.tsv - не править вручную.
 */
public final class TransportType {

    public static final String TABLE = "transport_type";

    /** Пассажирская - по умолчанию */
    public static final long PASSENGER = 1;
    /** Грузовая */
    public static final long FREIGHT = 2;

    private TransportType() {
    }

}
