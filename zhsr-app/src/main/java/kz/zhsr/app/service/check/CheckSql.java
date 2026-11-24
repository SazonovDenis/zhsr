package kz.zhsr.app.service.check;

import kz.zhsr.app.dto.check.CheckListFilter;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.service.SqlQuery;
import kz.zhsr.app.service.SqlWhere;
import kz.zhsr.app.service.TripWagonSql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL проверок по чек-листу ({@link CheckService}): запросы с соединениями и подсчётом, которые не покрываются методами
 * Db. Логики здесь нет.
 */
final class CheckSql {

    private CheckSql() {
    }

    /**
     * Строка проверки: поля чек-листа, число нарушений и созданных заявок. Вагон соединён для поиска по номеру.
     */
    private static final String SELECT = """
            select check_list.id, check_list.check_list_template, check_list.wagon, check_list.trip,
                   check_list.checked_usr, check_list.created_by_usr, check_list.created_dt, check_list.completed_dt,
                   (select count(*) from check_list_value
                    where check_list_value.check_list = check_list.id and check_list_value.violation) as violation_count,
                   (select count(*) from check_list_value
                    where check_list_value.check_list = check_list.id
                      and check_list_value.repair_request is not null) as request_count
            from check_list
            left join wagon on wagon.id = check_list.wagon
            """;

    /**
     * Поле строки -> выражение сортировки.
     */
    private static final Map<String, String> SORT = Map.of(
            "createdDt", "check_list.created_dt",
            "wagon", "wagon.number",
            "createdByUsr", "(select usr.full_name from usr where usr.id = check_list.created_by_usr)"
    );

    /**
     * Проверки по фильтру (Ф-ЧЕК-04); по умолчанию сначала новые.
     *
     * @param me      сотрудник
     * @param seesAll сотрудник видит все проверки; false - по scope
     * @param scope   что видно, кроме своих (CheckRights.scope)
     */
    static SqlQuery list(CheckListFilter filter, long me, boolean seesAll, CheckRights.ListScope scope, ListSort sort) {
        SqlWhere where = new SqlWhere();
        if (!seesAll) {
            visible(where, me, scope);
        }
        if (filter.template() != null) {
            where.add("check_list.check_list_template = :template", "template", filter.template());
        }
        if (filter.wagon() != null) {
            where.add("check_list.wagon = :wagon", "wagon", filter.wagon());
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            where.add("strpos(wagon.number, :query) > 0", "query", filter.query().trim());
        }
        if (filter.from() != null) {
            where.add("check_list.created_dt >= :from", "from", filter.from());
        }
        if (filter.to() != null) {
            where.add("check_list.created_dt < :toNext", "toNext", filter.to().plusDays(1));
        }
        return where.query(SELECT, sort.orderBy(SORT, "check_list.created_dt desc, check_list.id desc"));
    }

    /**
     * Условие видимости строки check_list сотруднику, который видит не все (CheckRights.seesAll): проведена им самим, по его
     * вагону или по вагону и рейсу его рейса (CheckRights.scope).
     */
    static void visible(SqlWhere where, long me, CheckRights.ListScope scope) {
        List<String> any = new ArrayList<>();
        any.add("check_list.created_by_usr = :me");
        where.param("me", me);
        if (!scope.wagons().isEmpty()) {
            any.add("check_list.wagon = any(:myWagons)");
            where.param("myWagons", scope.wagons());
        }
        if (scope.trip() != null) {
            any.add("check_list.wagon in " + TripWagonSql.wagonsOf("myTrip"));
            any.add("check_list.trip = :myTrip");
            where.param("myTrip", scope.trip());
            TripWagonSql.PARAMS.forEach(where::param);
        }
        where.add("(" + String.join(" or ", any) + ")");
    }

    /**
     * Видна ли check_list сотруднику (как в списке). Параметры - в запросе.
     */
    static SqlQuery visibleOne(long id, long me, CheckRights.ListScope scope) {
        SqlWhere where = new SqlWhere();
        where.add("check_list.id = :id", "id", id);
        visible(where, me, scope);
        return where.query("select check_list.id from check_list\n", "");
    }

    /**
     * Одна проверка. Параметр: id.
     */
    static String one() {
        return SELECT + "where check_list.id = :id";
    }

    /**
     * Удалить фото заявок, которые ссылаются на файл. Параметр: key (storage_key).
     */
    static String deleteRequestPhotos() {
        return "delete from repair_request_photo where storage_key = :key";
    }

}
