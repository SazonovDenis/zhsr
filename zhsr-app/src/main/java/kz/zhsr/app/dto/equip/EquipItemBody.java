package kz.zhsr.app.dto.equip;

/**
 * Новый или исправленный пункт шаблона описи; шаблон - из пути запроса или прежний.
 */
public record EquipItemBody(
        Long parentItem,
        String textRu,
        String textKk,
        String textEn,
        String unit,
        int sortOrder,
        boolean disabled
) {
}
