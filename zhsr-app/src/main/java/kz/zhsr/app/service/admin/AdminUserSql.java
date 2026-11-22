package kz.zhsr.app.service.admin;

/**
 * SQL редактора сотрудников ({@link AdminUserService}). Строка: id, login, full_name, phone, disabled, change_password,
 * depot и category_type - текущие (незакрытые) записи usr_depot и usr_category.
 */
final class AdminUserSql {

    private AdminUserSql() {
    }

    /**
     * Один сотрудник. Параметр: id.
     */
    static String row() {
        return """
                select usr.id, usr.login, usr.full_name, usr.phone, usr.disabled, usr.change_password, usr_depot.depot,
                       usr_category.category_type
                from usr
                left join usr_depot on usr_depot.usr = usr.id and usr_depot.dend is null
                left join usr_category on usr_category.usr = usr.id and usr_category.dend is null
                where usr.id = :id
                """;
    }

}
