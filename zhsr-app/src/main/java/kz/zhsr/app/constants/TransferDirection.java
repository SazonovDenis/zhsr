package kz.zhsr.app.constants;

/**
 * Направление акта приёма-передачи: id transfer_direction_type (db/dict/transfer_direction_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/transfer_direction_type.tsv - не править вручную.
 */
public final class TransferDirection {

    public static final String TABLE = "transfer_direction_type";

    /** В депо или парк отстоя - станция -> депо или парк отстоя этой станции */
    public static final long TO_DEPOT = 1;
    /** Из депо или парка отстоя - депо или парк отстоя -> станция */
    public static final long FROM_DEPOT = 2;

    private TransferDirection() {
    }

}
