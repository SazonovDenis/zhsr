package kz.zhsr.app.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Условия where запроса, собранного на ходу: условия и их параметры - вместе (см. {@link SqlQuery}). Пустое значение
 * фильтра условия не добавляет.
 */
public final class SqlWhere {

    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> params = new HashMap<>();

    public void add(String condition) {
        conditions.add(condition);
    }

    public void add(String condition, String param, Object value) {
        conditions.add(condition);
        params.put(param, value);
    }

    /**
     * Параметр запроса без условия: его использует сам текст select.
     */
    public void param(String name, Object value) {
        params.put(name, value);
    }

    /**
     * Условие видимости: строка видна, если выполнено хотя бы одно из условий; все null - не видно ничего.
     * Параметры условий: visDepot, visTrip, visWagons - из access.
     */
    public void visible(String byDepot, String byTrip, String byWagons, Visibility.Access access) {
        List<String> any = new ArrayList<>();
        for (String condition : new String[]{byDepot, byTrip, byWagons}) {
            if (condition != null) {
                any.add(condition);
            }
        }
        if (any.isEmpty()) {
            conditions.add("false");
            return;
        }
        conditions.add("(" + String.join("\n    or ", any) + ")");
        if (access.depot() != null) {
            params.put("visDepot", access.depot());
        }
        if (access.trip() != null) {
            params.put("visTrip", access.trip());
        }
        if (!access.wagons().isEmpty()) {
            params.put("visWagons", access.wagons());
        }
    }

    /**
     * column = any(:param); пустой список - без условия.
     */
    public void any(String column, String param, List<? extends Number> values) {
        if (values != null && !values.isEmpty()) {
            add(column + " = any(:" + param + ")", param, values);
        }
    }

    /**
     * Запрос: select ... from ... + where + order by.
     */
    public SqlQuery query(String select, String orderBy) {
        String where = conditions.isEmpty() ? "" : "where " + String.join("\n  and ", conditions) + "\n";
        return new SqlQuery(select + where + orderBy, params);
    }
}
