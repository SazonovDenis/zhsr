package kz.zhsr.app.constants;

/**
 * Стадия рейса записи журнала ответственных: значения wagon_responsible.trip_point. У плана - какую отметку рейса он
 * ждёт; у факта - какая отметка рейса его записала. Нет - факт рейса, внесённый не отметкой (смена ответственных в
 * рейсе).
 */
public final class WagonResponsibleTripPoint {

    /**
     * Отправление: план ответственных на рейс, его отрабатывает отметка отправления.
     */
    public static final int DEPARTURE = 1;

    /**
     * Прибытие: план ответственных после рейса, его отрабатывает отметка прибытия.
     */
    public static final int ARRIVAL = 2;

    private WagonResponsibleTripPoint() {
    }

}
