package kz.zhsr.app.service.dict;

import kz.kis.kiscore.model.*;
import kz.kis.kisdbtools.repository.*;
import kz.zhsr.app.auth.*;
import kz.zhsr.app.dto.*;
import kz.zhsr.app.service.*;
import org.springframework.messaging.simp.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.support.*;

import java.util.*;
import java.util.function.*;

/**
 * Справочники для DictManager фронта (shared/dict/DictManager.ts): только пополняемые справочники - их ведёт администрирование.
 * <p>
 * Системные справочники фронт не загружает: их записи и константы зашиты в код (zhsr-database/gen-constants.py).
 * <p>
 * Версия справочника - dict_version.version; справочник, который ни разу не менялся, - версия "0". После изменения
 * справочника ({@link #changed}) версия растёт, а после фиксации транзакции фронту уходит команда refreshDict в канал
 * WebSocket dict-manager.
 */
@Service
public class DictService {

    /**
     * Канал команд DictManager: destination /topic/dict-manager.
     */
    public static final String CHANNEL = "/topic/dict-manager";

    /**
     * Справочник: запрос и запись для фронта из строки запроса.
     */
    private record Def(String table, Function<DbRec, Map<String, Object>> record) {
    }

    private static final Map<String, Def> DICTS = Map.of(
            "WagonType", new Def("wagon_type", r -> rec(r, "text", r.getString("text"))),
            "WagonModel", new Def("model_type", r -> rec(r, "text", r.getString("text"))),
            "Depot", new Def("depot", r -> {
                Map<String, Object> res = rec(r, "text", multiText(r.getString("name"), r.getString("name_kk"),
                        r.getString("name_en")));
                res.put("station", r.getLong("station", null));
                return res;
            }),
            // hub - узловая станция (при ней есть депо): его добавляет records по списку депо
            "Station", new Def("station", r -> rec(r, "text", r.getString("name"))),
            "Yard", new Def("yard", r -> {
                Map<String, Object> res = rec(r, "text", r.getString("name"));
                res.put("station", r.getLong("station", null));
                return res;
            }),
            "FaultGroup", new Def("fault_group_type", r -> {
                Map<String, Object> res = rec(r, "text", multiText(r.getString("name"), null, null));
                res.put("sortOrder", r.getInt("sort_order"));
                return res;
            }),
            "FaultType", new Def("fault_type", r -> {
                Map<String, Object> res = rec(r, "text", multiText(r.getString("name"), null, null));
                res.put("faultGroupType", r.getLong("fault_group_type", null));
                res.put("faultCategoryType", r.getLong("fault_category_type"));
                res.put("severityType", r.getLong("severity_type"));
                res.put("service", r.getLong("service", null));
                res.put("disabled", r.getBoolean("disabled"));
                return res;
            })
    );

    private final Db db;
    private final CurrentUserService currentUserService;
    private final SimpMessagingTemplate messaging;

    public DictService(Db db, CurrentUserService currentUserService, SimpMessagingTemplate messaging) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.messaging = messaging;
    }

    /**
     * Справочник целиком (GET /dict/{name}).
     */
    public DictData dict(String name) throws Exception {
        currentUserService.get();
        Def def = def(name);
        List<Map<String, Object>> records = records(name, def, db.loadList(def.table(), null));
        return new DictData(version(name), records);
    }

    /**
     * Одна запись справочника (GET /dict/{name}/{id}); записи нет - record = null.
     */
    public DictValue value(String name, long id) throws Exception {
        currentUserService.get();
        Def def = def(name);
        DbRec rec = db.loadRec(def.table(), id);
        return new DictValue(version(name), rec == null ? null : records(name, def, List.of(rec)).get(0));
    }

    /**
     * Версии справочников (POST /dict/versions): только известных сервису, остальные имена пропускаются.
     */
    public Map<String, String> versions(List<String> names) throws Exception {
        currentUserService.get();
        Map<String, String> res = new LinkedHashMap<>();
        for (String name : names == null ? List.<String>of() : names) {
            if (DICTS.containsKey(name)) {
                res.put(name, version(name));
            }
        }
        return res;
    }

    /**
     * Справочник изменился: новая версия, а после фиксации транзакции - команда refreshDict фронту. Вызывается в
     * транзакции изменения.
     *
     * @param name имя справочника фронта
     */
    public void changed(String name) throws Exception {
        db.execSql(DictSql.bumpVersion(), Map.of("name", name));
        Map<String, Object> command = Map.of("cmd", "refreshDict", "dict", name);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messaging.convertAndSend(CHANNEL, command);
                }
            });
        } else {
            messaging.convertAndSend(CHANNEL, command);
        }
    }

    // --- внутреннее

    private static Def def(String name) {
        Def def = DICTS.get(name);
        if (def == null) {
            throw ApiException.notFound("Справочника " + name + " нет");
        }
        return def;
    }

    /**
     * Записи справочника для фронта. У станции - признак hub: при ней есть депо, с неё может начинаться и на ней
     * заканчиваться маршрут.
     */
    private List<Map<String, Object>> records(String name, Def def, List<DbRec> rows) throws Exception {
        Set<Long> hubs = new HashSet<>();
        if (name.equals("Station")) {
            for (DbRec depot : db.loadList("depot", null)) {
                hubs.add(depot.getLong("station"));
            }
        }
        List<Map<String, Object>> res = new ArrayList<>(rows.size());
        for (DbRec row : rows) {
            Map<String, Object> rec = def.record().apply(row);
            if (name.equals("Station")) {
                rec.put("hub", hubs.contains(row.getLong("id")));
            }
            res.add(rec);
        }
        return res;
    }

    private String version(String name) throws Exception {
        DbRec rec = db.loadRec("dict_version", Map.of("name", name));
        return rec == null ? "0" : String.valueOf(rec.getLong("version"));
    }

    /**
     * Запись: id, code и text.
     */
    private static Map<String, Object> rec(DbRec r, String textField, Object text) {
        Map<String, Object> res = new HashMap<>();
        res.put("id", r.getLong("id"));
        res.put("code", r.getString("code"));
        res.put(textField, text);
        return res;
    }

    /**
     * Текст на языках: { ru, kk, en }; языки без перевода не пишутся.
     */
    private static Map<String, String> multiText(String ru, String kk, String en) {
        Map<String, String> res = new LinkedHashMap<>();
        res.put("ru", ru);
        if (kk != null && !kk.isBlank()) {
            res.put("kk", kk);
        }
        if (en != null && !en.isBlank()) {
            res.put("en", en);
        }
        return res;
    }

}
