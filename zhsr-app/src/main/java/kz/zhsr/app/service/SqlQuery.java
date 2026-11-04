package kz.zhsr.app.service;

import java.util.Map;

/**
 * Запрос, собранный на ходу (условия поиска и фильтра зависят от запроса пользователя): текст SQL и его параметры -
 * вместе, потому что параметры появляются вместе с условиями. Постоянный SQL - просто строка.
 *
 * @param sql    текст SQL
 * @param params параметры по имени (:имя в тексте)
 */
public record SqlQuery(String sql, Map<String, Object> params) {
}
