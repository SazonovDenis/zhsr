package kz.zhsr.app.dto.admin;

import java.util.List;
import java.util.Map;

/**
 * Страница строк таблицы администрирования.
 *
 * @param total строк по поиску всего
 * @param rows  строки страницы: id, поля таблицы и {@code <поле>_text} - текст ссылки
 */
public record AdminPage<T>(long total, List<T> rows) {
    public AdminPage {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public static AdminPage<Map<String, Object>> empty() {
        return new AdminPage<>(0, List.of());
    }
}
