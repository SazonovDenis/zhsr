package kz.zhsr.app.dto;

import kz.zhsr.app.service.ApiException;

import java.util.Map;

/**
 * Сортировка списка из параметров запроса: {@code sort=<поле строки>&desc=true}.
 *
 * @param sort имя поля строки списка, как его передаёт zhsr-web; null или пусто - порядок по умолчанию
 * @param desc true - по убыванию; параметра нет - false (Boolean, а не boolean: без параметра Spring не разберёт null в
 *             boolean)
 */
public record ListSort(String sort, Boolean desc) {

    public ListSort {
        desc = Boolean.TRUE.equals(desc);
    }

    /**
     * Текст ORDER BY.
     *
     * @param fields      разрешённые поля: поле строки - выражение SQL; поле не из карты - ошибка 400
     * @param defaultSort порядок по умолчанию, если поле не задано; он же добавляется после выбранного поля
     *                    для устойчивого порядка одинаковых значений
     */
    public String orderBy(Map<String, String> fields, String defaultSort) {
        if (sort == null || sort.isBlank()) {
            return " order by " + defaultSort;
        }
        String expr = fields.get(sort);
        if (expr == null) {
            throw ApiException.badRequest("Сортировка по полю '" + sort + "' не поддерживается");
        }
        return " order by " + expr + (desc ? " desc" : "") + ", " + defaultSort;
    }

}
