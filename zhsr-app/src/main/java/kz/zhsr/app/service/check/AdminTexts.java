package kz.zhsr.app.service.check;

import kz.zhsr.app.service.ApiException;

import java.util.Map;

/**
 * Названия на трёх языках в колонках записи: text_ru обязателен, text_kk и text_en - пусто, если не заданы. Пробелы
 * по краям отбрасываются.
 */
public final class AdminTexts {

    private AdminTexts() {
    }

    /**
     * Привести названия в колонках записи.
     *
     * @throws ApiException 400 - нет названия на русском
     */
    public static void normalize(Map<String, Object> columns) {
        String ru = trim(columns.get("text_ru"));
        if (ru == null) {
            throw ApiException.badRequest("Укажите название на русском");
        }
        columns.put("text_ru", ru);
        columns.put("text_kk", trim(columns.get("text_kk")));
        columns.put("text_en", trim(columns.get("text_en")));
    }

    /**
     * @return строка без пробелов по краям; null - пусто
     */
    public static String trim(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

}
