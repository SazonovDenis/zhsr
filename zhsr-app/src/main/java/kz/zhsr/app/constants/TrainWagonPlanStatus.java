package kz.zhsr.app.constants;

/**
 * Статус плана прицепки или отцепки: значения train_wagon_change.plan_status, только у плана.
 */
public final class TrainWagonPlanStatus {

    /**
     * Ожидает: факт по плану ещё не отмечен; план входит в ожидаемый состав поезда.
     */
    public static final int WAITING = 1;

    /**
     * Отработан: отмечен факт по плану, совпали время и место с планом или нет.
     */
    public static final int DONE = 2;

    /**
     * Отменён: план не состоялся.
     */
    public static final int CANCELLED = 3;

    private TrainWagonPlanStatus() {
    }

}
