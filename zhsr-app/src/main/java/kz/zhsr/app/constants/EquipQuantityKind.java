package kz.zhsr.app.constants;

/**
 * Как сравнивать фактическое количество с требуемым: значения equip_list_item_norm.quantity_kind.
 */
public final class EquipQuantityKind {

    /**
     * Ровно: нарушение - любое отличие.
     */
    public static final int EXACT = 1;

    /**
     * Не менее: нарушение - только недостача.
     */
    public static final int AT_LEAST = 2;

    private EquipQuantityKind() {
    }

}
