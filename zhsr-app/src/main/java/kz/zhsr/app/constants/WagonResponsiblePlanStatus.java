package kz.zhsr.app.constants;

/**
 * Статус плана ответственных за вагон: значения wagon_responsible.plan_status, только у плана.
 */
public final class WagonResponsiblePlanStatus {

    /**
     * Ожидает: факт по плану ещё не отмечен.
     */
    public static final int WAITING = 1;

    /**
     * Отработан: отмечен факт по плану, совпали даты с планом или нет.
     */
    public static final int DONE = 2;

    /**
     * Отменён: план не состоялся.
     */
    public static final int CANCELLED = 3;

    private WagonResponsiblePlanStatus() {
    }

}
