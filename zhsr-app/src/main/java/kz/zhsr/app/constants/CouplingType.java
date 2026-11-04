package kz.zhsr.app.constants;

/**
 * Отцепка или прицепка: id coupling_type (db/dict/coupling_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/coupling_type.tsv - не править вручную.
 */
public final class CouplingType {

    public static final String TABLE = "coupling_type";

    /** Отцепка */
    public static final long DETACH = 1;
    /** Прицепка */
    public static final long ATTACH = 2;

    private CouplingType() {
    }

}
