package kz.zhsr.app.dto.check;

/**
 * Новый или исправленный пункт шаблона чек-листа; шаблон - из пути запроса или прежний.
 */
public record CheckItemBody(
        Long parentItem,
        String textRu,
        String textKk,
        String textEn,
        Integer valueKind,
        Long checkDict,
        int sortOrder,
        boolean disabled
) {
}
