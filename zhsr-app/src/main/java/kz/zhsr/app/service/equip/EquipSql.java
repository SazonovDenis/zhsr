package kz.zhsr.app.service.equip;

import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.equip.EquipListFilter;
import kz.zhsr.app.service.SqlQuery;
import kz.zhsr.app.service.SqlWhere;
import kz.zhsr.app.service.TripWagonSql;
import kz.zhsr.app.service.check.CheckRights;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL описей экипировки ({@link EquipService}): запросы с соединениями, подсчётом и порядком, которые не покрываются
 * методами Db. Логики здесь нет.
 */
final class EquipSql {

    private EquipSql() {
    }

    /**
     * Строка описи: поля описи и число нарушений. Вагон соединён для поиска по номеру.
     */
    private static final String SELECT = """
            select equip_list.id, equip_list.equip_list_template, equip_list.wagon, equip_list.trip,
                   equip_list.created_by_usr, equip_list.created_dt, equip_list.completed_dt, equip_list.repair_request,
                   (select count(*) from equip_list_value
                    where equip_list_value.equip_list = equip_list.id and equip_list_value.violation) as violation_count
            from equip_list
            join wagon on wagon.id = equip_list.wagon
            """;

    /**
     * Поле строки -> выражение сортировки.
     */
    private static final Map<String, String> SORT = Map.of(
            "createdDt", "equip_list.created_dt",
            "wagon", "wagon.number",
            "createdByUsr", "(select usr.full_name from usr where usr.id = equip_list.created_by_usr)"
    );

    /**
     * Описи по фильтру (Ф-ЭКП-03); по умолчанию сначала новые.
     *
     * @param me      сотрудник
     * @param seesAll сотрудник видит все описи; false - только составленные им
     */
    static SqlQuery list(EquipListFilter filter, long me, boolean seesAll, CheckRights.ListScope scope, ListSort sort) {
        SqlWhere where = new SqlWhere();
        if (!seesAll) {
            visible(where, me, scope);
        }
        if (filter.template() != null) {
            where.add("equip_list.equip_list_template = :template", "template", filter.template());
        }
        if (filter.wagon() != null) {
            where.add("equip_list.wagon = :wagon", "wagon", filter.wagon());
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            where.add("strpos(wagon.number, :query) > 0", "query", filter.query().trim());
        }
        if (filter.from() != null) {
            where.add("equip_list.created_dt >= :from", "from", filter.from());
        }
        if (filter.to() != null) {
            where.add("equip_list.created_dt < :toNext", "toNext", filter.to().plusDays(1));
        }
        return where.query(SELECT, sort.orderBy(SORT, "equip_list.created_dt desc, equip_list.id desc"));
    }

    /**
     * Условие видимости строки equip_list сотруднику, который видит не все (CheckRights.seesAll): проведена им самим, по его
     * вагону или по вагону и рейсу его рейса (CheckRights.scope).
     */
    static void visible(SqlWhere where, long me, CheckRights.ListScope scope) {
        List<String> any = new ArrayList<>();
        any.add("equip_list.created_by_usr = :me");
        where.param("me", me);
        if (!scope.wagons().isEmpty()) {
            any.add("equip_list.wagon = any(:myWagons)");
            where.param("myWagons", scope.wagons());
        }
        if (scope.trip() != null) {
            any.add("equip_list.wagon in " + TripWagonSql.wagonsOf("myTrip"));
            any.add("equip_list.trip = :myTrip");
            where.param("myTrip", scope.trip());
            TripWagonSql.PARAMS.forEach(where::param);
        }
        where.add("(" + String.join(" or ", any) + ")");
    }

    /**
     * Видна ли equip_list сотруднику (как в списке). Параметры - в запросе.
     */
    static SqlQuery visibleOne(long id, long me, CheckRights.ListScope scope) {
        SqlWhere where = new SqlWhere();
        where.add("equip_list.id = :id", "id", id);
        visible(where, me, scope);
        return where.query("select equip_list.id from equip_list\n", "");
    }

    /**
     * Одна опись. Параметр: id.
     */
    static String one() {
        return SELECT + "where equip_list.id = :id";
    }

    /**
     * Последняя опись вагона по шаблону, кроме описи exclude. Параметры: wagon, template, exclude (0 - не исключать).
     * Колонка id; строк нет - описей не было.
     */
    static String last() {
        return """
                select equip_list.id
                from equip_list
                where equip_list.wagon = :wagon and equip_list.equip_list_template = :template
                  and equip_list.id <> :exclude
                order by equip_list.completed_dt desc, equip_list.id desc
                limit 1
                """;
    }

    /**
     * Удалить фото заявок, которые ссылаются на файл. Параметр: key (storage_key).
     */
    static String deleteRequestPhotos() {
        return "delete from repair_request_photo where storage_key = :key";
    }

    /**
     * Последняя опись каждого вагона по каждому шаблону и число позиций в недостаче (фактическое количество меньше
     * требуемого); отбор последней и недостачи - как в готовности рейса (TripReadinessLoader). Параметр wagons.
     */
    static String shortages() {
        return """
                select equip_list.id, equip_list.equip_list_template, equip_list.wagon, equip_list.created_dt,
                       (select count(*) from equip_list_value
                        where equip_list_value.equip_list = equip_list.id
                          and equip_list_value.quantity_required is not null
                          and equip_list_value.quantity_actual < equip_list_value.quantity_required) as shortage_count
                from equip_list
                join equip_list_template on equip_list_template.id = equip_list.equip_list_template
                where equip_list.wagon = any(:wagons)
                  and equip_list.id = (select last.id from equip_list as last
                                       where last.wagon = equip_list.wagon
                                         and last.equip_list_template = equip_list.equip_list_template
                                       order by last.completed_dt desc, last.id desc
                                       limit 1)
                order by equip_list.wagon, equip_list_template.sort_order, equip_list_template.text_ru
                """;
    }

    /** Последняя опись вагона: id, по времени составления. Параметр wagon. */
    static String lastOfWagon() {
        return """
                select equip_list.id
                from equip_list
                where equip_list.wagon = :wagon
                order by equip_list.created_dt desc, equip_list.id desc
                limit 1
                """;
    }

}
