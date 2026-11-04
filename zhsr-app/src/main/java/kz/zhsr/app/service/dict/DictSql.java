package kz.zhsr.app.service.dict;

/**
 * SQL справочников фронта ({@link DictService}): только то, что не выражается методами Db. Записи справочников
 * загружаются методами Db (loadList, loadRec).
 */
final class DictSql {

    private DictSql() {
    }

    /**
     * Новая версия справочника: версия растёт на 1, первая версия - 1. Параметр: name.
     */
    static String bumpVersion() {
        return """
                insert into dict_version (name, version, changed_dt)
                values (:name, 1, now())
                on conflict (name) do update set version = dict_version.version + 1, changed_dt = now()
                """;
    }

}
