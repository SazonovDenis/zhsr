package kz.zhsr.app.dto.equip;

/**
 * Новый или исправленный шаблон описи экипировки (equip_list_template).
 */
public record EquipTemplateBody(
        String textRu,
        String textKk,
        String textEn,
        Long faultType,
        int sortOrder,
        boolean disabled
) {
}
