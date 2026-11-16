package kz.zhsr.app.dto.check;

/**
 * Новый или исправленный вариант ответа (check_dict_value); шаблон ответов - из пути запроса.
 */
public record CheckDictValueBody(
        String textRu,
        String textKk,
        String textEn,
        int sortOrder,
        boolean disabled
) {
}
