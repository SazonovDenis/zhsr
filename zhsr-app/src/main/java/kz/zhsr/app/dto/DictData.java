package kz.zhsr.app.dto;

import java.util.List;
import java.util.Map;

/**
 * Справочник для DictManager фронта: версия и записи.
 *
 * @param version версия справочника: меняется при каждом изменении справочника
 * @param records записи: id, text и поля справочника (shared/dict/dicts.ts)
 */
public record DictData(String version, List<Map<String, Object>> records) {
    public DictData {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
