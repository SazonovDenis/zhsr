package kz.zhsr.app.constants;

/**
 * Слой журнала прицепок и отцепок и участия вагона в поезде: значения train_wagon_change.kind и train_wagon.kind.
 */
public final class TrainWagonKind {

    /**
     * План: у журнала - запланированная прицепка или отцепка; у участия - ожидаемый состав поезда.
     */
    public static final int PLAN = 1;

    /**
     * Факт: у журнала - прицепка или отцепка, которая состоялась; у участия - история состава поезда.
     */
    public static final int FACT = 2;

    private TrainWagonKind() {
    }

}
