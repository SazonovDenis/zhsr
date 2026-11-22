package kz.zhsr.app.constants;

/**
 * Что показывают и меняют «Ответственные за вагоны рейса»: параметр layer запросов /trips/{id}/responsibles.
 */
public final class TripResponsibleLayer {

    /**
     * Ответственные на рейс: до отправления - план ответственных на рейс (с планового отправления), после - факт.
     */
    public static final int TRIP = 1;

    /**
     * План ответственных после рейса: с планового прибытия; правится до отметки прибытия.
     */
    public static final int AFTER = 2;

    /**
     * Окно отметки прибытия: у вагона с планом после рейса - план, у остальных - текущие ответственные.
     */
    public static final int ARRIVAL = 3;

    /**
     * Факт: текущие ответственные вагонов поезда рейса в любом статусе рейса, кроме «отменён»; изменения
     * записываются фактом временем изменения.
     */
    public static final int FACT = 4;

    private TripResponsibleLayer() {
    }

}
