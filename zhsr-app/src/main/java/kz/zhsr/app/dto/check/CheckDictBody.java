package kz.zhsr.app.dto.check;

/**
 * Новый или исправленный шаблон ответов (check_dict).
 */
public record CheckDictBody(
        String textRu,
        String textKk,
        String textEn,
        int sortOrder,
        boolean disabled
) {
}
