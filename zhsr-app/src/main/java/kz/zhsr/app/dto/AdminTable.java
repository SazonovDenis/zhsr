package kz.zhsr.app.dto;

import java.util.List;

/**
 * Таблица, которую администратор редактирует общим редактором.
 *
 * @param name    имя таблицы в БД; путь /admin/tables/{name}
 * @param title   заголовок на русском
 * @param columns поля, кроме id
 */
public record AdminTable(String name, String title, List<AdminColumn> columns) {
    public AdminTable {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
