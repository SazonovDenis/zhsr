package kz.zhsr.app.constants;

/**
 * Вид расположения вагона: id location_type (db/dict/location_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/location_type.tsv - не править вручную.
 */
public final class LocationType {

    public static final String TABLE = "location_type";

    /** Станция */
    public static final long STATION = 1;
    /** Депо */
    public static final long DEPOT = 2;
    /** В рейсе */
    public static final long TRIP = 3;
    /** Парк отстоя */
    public static final long YARD = 4;

    private LocationType() {
    }

}
