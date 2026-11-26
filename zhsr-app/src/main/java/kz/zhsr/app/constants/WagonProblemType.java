package kz.zhsr.app.constants;

/**
 * Вид неготовности вагона: значения WagonProblem.wagonProblemType. Вычисляется, в БД не хранится.
 */
public final class WagonProblemType {

    /** Вагон будет не на той станции: нужен перегоночный рейс. */
    public static final int STATION = 1;

    /** Вагон на нужной станции, но в депо или парке отстоя: нужна передача на станцию. */
    public static final int YARD = 2;

    /** Вагон в это время в другом поезде; расположение тогда не проверяется. */
    public static final int BUSY = 3;

    private WagonProblemType() {
    }

}
