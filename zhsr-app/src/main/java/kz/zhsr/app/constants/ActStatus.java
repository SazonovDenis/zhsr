package kz.zhsr.app.constants;

/**
 * Статус акта: id act_status_type (db/dict/act_status_type.tsv). Системный справочник: значения меняются только вместе с
 * кодом; при старте {@link SystemDicts} сверяет их с БД.
 * <p>
 * Сгенерировано zhsr-database/gen-constants.py из db/dict/act_status_type.tsv - не править вручную.
 */
public final class ActStatus {

    public static final String TABLE = "act_status_type";

    /** Ожидает приёма - акт приёма-передачи сдан, приём не отмечен; расположение вагонов не меняется */
    public static final long PENDING = 1;
    /** Проведён - акт действует; акт приёма-передачи принят: расположение вагонов изменено */
    public static final long DONE = 2;
    /** Отказ - принимающая сторона отказала в приёме с причиной; расположение вагонов не меняется */
    public static final long REJECTED = 3;
    /** Отменён - акт отменён актом отмены act_cancellation; записи журналов, открытые актом, удалены */
    public static final long CANCELLED = 4;

    private ActStatus() {
    }

}
