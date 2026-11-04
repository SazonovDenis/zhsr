package kz.zhsr.app.service.admin;

import kz.zhsr.app.service.SqlQuery;

import java.util.HashMap;
import java.util.Map;

/**
 * SQL редактора сотрудников ({@link AdminUserService}). Строка: id, login, full_name, phone, disabled, change_password,
 * depot и category_type - текущие (незакрытые) записи usr_depot и usr_category.
 */
final class AdminUserSql {

    /**
     * Поле сортировки списка -> выражение SQL.
     */
    static final Map<String, String> SORT = Map.of(
            "login", "usr.login",
            "fullName", "usr.full_name"
    );

    private AdminUserSql() {
    }

    static SqlQuery page(String search, String orderBy, boolean desc, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", limit);
        params.put("offset", offset);
        return new SqlQuery(select() + where(search, params)
                + "order by " + orderBy + (desc ? " desc" : "") + ", usr.id\n"
                + "limit :limit offset :offset", params);
    }

    /**
     * Сколько сотрудников найдено поиском. Поле: total.
     */
    static SqlQuery count(String search) {
        Map<String, Object> params = new HashMap<>();
        return new SqlQuery("select count(*) as total from usr\n" + where(search, params), params);
    }

    /**
     * Один сотрудник. Параметр: id.
     */
    static String row() {
        return select() + "where usr.id = :id\n";
    }

    private static String select() {
        return """
                select usr.id, usr.login, usr.full_name, usr.phone, usr.disabled, usr.change_password, usr_depot.depot,
                       usr_category.category_type
                from usr
                left join usr_depot on usr_depot.usr = usr.id and usr_depot.dend is null
                left join usr_category on usr_category.usr = usr.id and usr_category.dend is null
                """;
    }

    private static String where(String search, Map<String, Object> params) {
        if (search == null || search.isBlank()) {
            return "";
        }
        params.put("search", "%" + search.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        return "where (usr.login ilike :search or usr.full_name ilike :search or usr.phone ilike :search)\n";
    }

}
