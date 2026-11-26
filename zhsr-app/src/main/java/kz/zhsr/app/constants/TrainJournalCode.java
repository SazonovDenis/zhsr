package kz.zhsr.app.constants;

/**
 * Что за запись журнала поезда: имя таблицы записи. Запись журнала определяют код и id: id двух таблиц независимы.
 */
public final class TrainJournalCode {

    /**
     * Прицепка или отцепка вагона.
     */
    public static final String TRAIN_WAGON_CHANGE = "train_wagon_change";

    /**
     * Смена порядка вагонов.
     */
    public static final String TRAIN_WAGON_SEQ = "train_wagon_seq";

    private TrainJournalCode() {
    }

}
