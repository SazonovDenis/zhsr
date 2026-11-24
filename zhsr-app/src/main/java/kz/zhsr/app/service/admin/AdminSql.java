package kz.zhsr.app.service.admin;

import kz.zhsr.app.dto.admin.AdminColumn;
import kz.zhsr.app.dto.admin.AdminTable;
import kz.zhsr.app.service.SqlQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL общего редактора администрирования ({@link AdminService}): только страница строк с поиском - её не выразить
 * методами Db. Одна запись, тексты ссылок и варианты для полей-ссылок загружаются методами Db. Имена таблиц и колонок
 * - только из {@link AdminTables}.
 */
final class AdminSql {

    private AdminSql() {
    }

    /**
     * Страница строк таблицы: id и поля таблицы. Поиск - подстрока без учёта регистра в текстовых полях и в тексте
     * записей, на которые ссылаются поля-ссылки.
     *
     * @param orderBy поле сортировки - колонка таблицы из её описания
     */
    static SqlQuery page(AdminTable table, String search, String orderBy, boolean desc, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", limit);
        params.put("offset", offset);
        String sql = select(table) + searchWhere(table, search, params)
                + "order by " + orderBy + (desc ? " desc" : "") + ", id\n"
                + "limit :limit offset :offset";
        return new SqlQuery(sql, params);
    }

    /**
     * Сколько строк найдено поиском. Поле: total.
     */
    static SqlQuery count(AdminTable table, String search) {
        Map<String, Object> params = new HashMap<>();
        return new SqlQuery("select count(*) as total from " + table.name() + "\n"
                + searchWhere(table, search, params), params);
    }

    /**
     * Сортировка по полю-ссылке: текст записи, на которую ссылается поле.
     */
    static String refSort(AdminTable table, AdminColumn column) {
        return "(select " + column.ref() + "." + AdminTables.textColumn(column.ref()) + " from " + column.ref()
               + " where " + column.ref() + ".id = " + table.name() + "." + column.name() + ")";
    }

    private static String select(AdminTable table) {
        List<String> cols = new ArrayList<>();
        cols.add("id");
        for (AdminColumn c : table.columns()) {
            if (!AdminTables.isVirtual(table.name(), c.name())) {
                cols.add(c.name());
            }
        }
        return "select " + String.join(", ", cols) + "\nfrom " + table.name() + "\n";
    }

    private static String searchWhere(AdminTable table, String search, Map<String, Object> params) {
        if (search == null || search.isBlank()) {
            return "";
        }
        params.put("search", search.trim());
        List<String> any = new ArrayList<>();
        for (AdminColumn c : table.columns()) {
            if (AdminTables.TEXT.equals(c.type())) {
                any.add("strpos(lower(" + c.name() + "), lower(:search)) > 0");
            } else if (AdminTables.REF.equals(c.type())) {
                any.add(c.name() + " in (select id from " + c.ref() + " where strpos(lower("
                        + AdminTables.textColumn(c.ref()) + "), lower(:search)) > 0)");
            }
        }
        return any.isEmpty() ? "where false\n" : "where (" + String.join(" or ", any) + ")\n";
    }

}
