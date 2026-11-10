package kz.zhsr.app.constants;

/**
 * Вид значения пункта чек-листа: значения check_list_template_item.value_kind. Пусто - раздел.
 */
public final class CheckValueKind {

    /**
     * Строка: check_list_value.value_string.
     */
    public static final int STRING = 1;

    /**
     * Число: check_list_value.value_number.
     */
    public static final int NUMBER = 2;

    /**
     * Значение справочника check_dict: check_list_value.check_dict_value.
     */
    public static final int DICT_VALUE = 3;

    private CheckValueKind() {
    }

}
