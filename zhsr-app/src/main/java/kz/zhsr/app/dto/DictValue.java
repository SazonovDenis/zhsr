package kz.zhsr.app.dto;

import java.util.Map;

/**
 * Одна запись справочника.
 *
 * @param version версия справочника
 * @param record  запись; null - записи больше нет
 */
public record DictValue(String version, Map<String, Object> record) {
}
