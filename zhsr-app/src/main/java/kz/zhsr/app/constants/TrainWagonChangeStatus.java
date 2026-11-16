package kz.zhsr.app.constants;

/**
 * Статус изменения участия вагона в поезде: значения train_wagon_change.status.
 */
public final class TrainWagonChangeStatus {

    /**
     * План: изменение запланировано, журналы вагона не менялись.
     */
    public static final int PLANNED = 1;

    /**
     * Выполнено: факт отмечен.
     */
    public static final int DONE = 2;

    /**
     * Отменено: план изменения отменён, изменение не состоялось, журналы вагона не менялись.
     */
    public static final int CANCELLED = 3;

    private TrainWagonChangeStatus() {
    }

}
