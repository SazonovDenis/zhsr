package kz.zhsr.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.util.HashMap;
import java.util.Map;

/**
 * Колонки записи из тела запроса: поля тела называются как колонки в camelCase (textRu - text_ru), значения
 * переносятся как есть (числа, строки, признаки). Поля, которых нет в таблице, вызывающий убирает сам.
 */
public final class Columns {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // дробные числа (numeric) - BigDecimal, а не double
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private Columns() {
    }

    /**
     * @return изменяемая карта колонка - значение
     */
    public static Map<String, Object> of(Object body) {
        return new HashMap<>(MAPPER.convertValue(body, new TypeReference<Map<String, Object>>() {
        }));
    }

}
