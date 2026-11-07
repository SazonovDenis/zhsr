package kz.zhsr.app.dto.admin;

/**
 * Поле таблицы администрирования: из него фронт строит колонку таблицы и поле формы.
 *
 * @param name      имя колонки в БД; оно же поле строки
 * @param title     заголовок на русском
 * @param type      вид значения: TEXT, INT, DECIMAL, BOOL, DATE, REF
 * @param required  обязательно для заполнения
 * @param maxLength наибольшая длина строки (TEXT); null - не ограничена
 * @param ref       таблица, на которую ссылается поле (REF): варианты - GET /admin/options/{ref}; текст значения в строке -
 *                  поле {@code <name>_text}
 * @param listed    показывается колонкой в таблице; false - только в форме
 */
public record AdminColumn(String name, String title, String type, boolean required, Integer maxLength, String ref,
                          boolean listed) {
}
