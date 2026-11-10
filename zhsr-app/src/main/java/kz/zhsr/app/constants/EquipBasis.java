package kz.zhsr.app.constants;

/**
 * База расчёта нормы экипировки: значения equip_list_item_norm.basis. Требуется = quantity_per_basis * база + добавка для головного
 * и хвостового вагона.
 */
public final class EquipBasis {

    /**
     * Вагон: база 1.
     */
    public static final int WAGON = 1;

    /**
     * Место: model_type.seats.
     */
    public static final int SEAT = 2;

    /**
     * Купе: model_type.compartments.
     */
    public static final int COMPARTMENT = 3;

    /**
     * Окно: model_type.windows.
     */
    public static final int WINDOW = 4;

    private EquipBasis() {
    }

}
