package kz.zhsr.app.service.check;

/**
 * SQL администрирования чек-листов и описей ({@link CheckAdminService}, {@link kz.zhsr.app.service.equip.EquipAdminService}):
 * запросы, которые не покрываются методами Db. Логики здесь нет.
 */
public final class CheckAdminSql {

    private CheckAdminSql() {
    }

    /**
     * По нормативу чек-листа проверены значения. Параметр: id - check_list_item_norm.id. Колонка used.
     */
    static String checkNormUsed() {
        return """
                select exists(select 1 from check_list_value where check_list_value.check_list_item_norm = :id) as used
                """;
    }

    /**
     * По нормативу описи посчитаны значения. Параметр: id - equip_list_item_norm.id. Колонка used.
     */
    public static String equipNormUsed() {
        return """
                select exists(select 1 from equip_list_value where equip_list_value.equip_list_item_norm = :id) as used
                """;
    }

    /**
     * Удалить допустимые варианты ответа нормативов пунктов шаблона чек-листа. Параметр: template.
     */
    static String deleteTemplateNormValues() {
        return """
                delete from check_list_item_norm_value
                where check_list_item_norm_value.check_list_item_norm in (
                    select check_list_item_norm.id
                    from check_list_item_norm
                    join check_list_template_item on check_list_template_item.id = check_list_item_norm.check_list_template_item
                    where check_list_template_item.check_list_template = :template)
                """;
    }

    /**
     * Удалить нормативы пунктов шаблона чек-листа. Параметр: template.
     */
    static String deleteTemplateNorms() {
        return """
                delete from check_list_item_norm
                where check_list_item_norm.check_list_template_item in (
                    select check_list_template_item.id
                    from check_list_template_item
                    where check_list_template_item.check_list_template = :template)
                """;
    }

    /**
     * Удалить допустимые варианты ответа нормативов пункта чек-листа. Параметр: item.
     */
    static String deleteItemNormValues() {
        return """
                delete from check_list_item_norm_value
                where check_list_item_norm_value.check_list_item_norm in (
                    select check_list_item_norm.id
                    from check_list_item_norm
                    where check_list_item_norm.check_list_template_item = :item)
                """;
    }

    /**
     * Удалить нормативы пунктов шаблона описи. Параметр: template.
     */
    public static String deleteEquipTemplateNorms() {
        return """
                delete from equip_list_item_norm
                where equip_list_item_norm.equip_list_template_item in (
                    select equip_list_template_item.id
                    from equip_list_template_item
                    where equip_list_template_item.equip_list_template = :template)
                """;
    }

}
