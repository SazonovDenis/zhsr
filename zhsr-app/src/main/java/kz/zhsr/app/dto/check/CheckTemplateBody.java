package kz.zhsr.app.dto.check;

/**
 * Новый или исправленный шаблон чек-листа (check_list_template).
 */
public record CheckTemplateBody(
        String textRu,
        String textKk,
        String textEn,
        int checkObject,
        int sortOrder,
        boolean disabled
) {
}
