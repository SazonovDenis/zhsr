package kz.zhsr.app.constants;

/**
 * Слой журнала ответственных за вагон: значения wagon_responsible.kind.
 */
public final class WagonResponsibleKind {

    /**
     * План: запланированный период и состав ответственных; журнал фактов не меняет.
     */
    public static final int PLAN = 1;

    /**
     * Факт: период и состав ответственных, которые были на деле.
     */
    public static final int FACT = 2;

    private WagonResponsibleKind() {
    }

}
