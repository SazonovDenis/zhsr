package kz.zhsr.app.service.check;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.constants.CheckValueKind;
import kz.zhsr.app.dto.check.CheckDictBody;
import kz.zhsr.app.dto.check.CheckDictRow;
import kz.zhsr.app.dto.check.CheckDictValueBody;
import kz.zhsr.app.dto.check.CheckDictValueRow;
import kz.zhsr.app.dto.check.CheckItemBody;
import kz.zhsr.app.dto.check.CheckItemRow;
import kz.zhsr.app.dto.check.CheckNormBody;
import kz.zhsr.app.dto.check.CheckNormRow;
import kz.zhsr.app.dto.check.CheckTemplateBody;
import kz.zhsr.app.dto.check.CheckTemplateRow;
import kz.zhsr.app.service.ApiException;
import kz.zhsr.app.service.Columns;
import kz.zhsr.app.service.DbErrors;
import kz.zhsr.app.service.admin.AdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Администрирование чек-листов: шаблоны ответов и варианты ответа (Ф-ЧЕК-01), шаблоны чек-листов, пункты и нормативы
 * (Ф-ЧЕК-02). Только для роли ADMIN.
 * <p>
 * Запись, на которую ссылаются заполненные чек-листы, не удаляется (409) - её отключают (disabled). Норматив, по
 * которому проверены значения, при правке и удалении закрывается (dend), правка заводит новый норматив с того же
 * момента; текущий норматив - незакрытый.
 */
@Service
public class CheckAdminService {

    private final Db db;
    private final AdminService adminService;

    public CheckAdminService(Db db, AdminService adminService) {
        this.db = db;
        this.adminService = adminService;
    }

    // --- шаблоны ответов

    public List<CheckDictRow> dicts() throws Exception {
        adminService.requireAdmin();
        List<CheckDictRow> res = new ArrayList<>(db.loadList("check_dict", Map.of(), CheckDictRow.class));
        res.sort(Comparator.comparingInt(CheckDictRow::getSortOrder).thenComparing(CheckDictRow::getTextRu));
        return res;
    }

    @Transactional
    public CheckDictRow createDict(CheckDictBody body) throws Exception {
        adminService.requireAdmin();
        Map<String, Object> columns = Columns.of(body);
        AdminTexts.normalize(columns);
        long id = insert("check_dict", columns);
        return db.loadRec("check_dict", id, true, CheckDictRow.class);
    }

    @Transactional
    public CheckDictRow updateDict(long id, CheckDictBody body) throws Exception {
        adminService.requireAdmin();
        rec("check_dict", id, "Шаблон ответов не найден");
        Map<String, Object> columns = Columns.of(body);
        AdminTexts.normalize(columns);
        update("check_dict", id, columns);
        return db.loadRec("check_dict", id, true, CheckDictRow.class);
    }

    /**
     * Удалить шаблон ответов вместе с вариантами ответа.
     *
     * @throws ApiException 409 - шаблон ответов выбран у пунктов или варианты ответа - в нормативах и чек-листах
     */
    @Transactional
    public void deleteDict(long id) throws Exception {
        adminService.requireAdmin();
        rec("check_dict", id, "Шаблон ответов не найден");
        try {
            db.deleteRec("check_dict_value", Map.of("check_dict", id));
            db.deleteRec("check_dict", id);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    public List<CheckDictValueRow> dictValues(long dict) throws Exception {
        adminService.requireAdmin();
        rec("check_dict", dict, "Шаблон ответов не найден");
        return sortedValues(db.loadList("check_dict_value", Map.of("check_dict", dict), CheckDictValueRow.class));
    }

    @Transactional
    public CheckDictValueRow createDictValue(long dict, CheckDictValueBody body) throws Exception {
        adminService.requireAdmin();
        rec("check_dict", dict, "Шаблон ответов не найден");
        Map<String, Object> columns = Columns.of(body);
        AdminTexts.normalize(columns);
        columns.put("check_dict", dict);
        long id = insert("check_dict_value", columns);
        return db.loadRec("check_dict_value", id, true, CheckDictValueRow.class);
    }

    @Transactional
    public CheckDictValueRow updateDictValue(long id, CheckDictValueBody body) throws Exception {
        adminService.requireAdmin();
        rec("check_dict_value", id, "Вариант ответа не найден");
        Map<String, Object> columns = Columns.of(body);
        AdminTexts.normalize(columns);
        update("check_dict_value", id, columns);
        return db.loadRec("check_dict_value", id, true, CheckDictValueRow.class);
    }

    /**
     * @throws ApiException 409 - вариант ответа выбран в нормативах или чек-листах
     */
    @Transactional
    public void deleteDictValue(long id) throws Exception {
        adminService.requireAdmin();
        rec("check_dict_value", id, "Вариант ответа не найден");
        delete("check_dict_value", id);
    }

    // --- шаблоны чек-листов

    public List<CheckTemplateRow> templates() throws Exception {
        adminService.requireAdmin();
        return sortedTemplates(db.loadList("check_list_template", Map.of(), CheckTemplateRow.class));
    }

    @Transactional
    public CheckTemplateRow createTemplate(CheckTemplateBody body) throws Exception {
        adminService.requireAdmin();
        long id = insert("check_list_template", templateColumns(body));
        return db.loadRec("check_list_template", id, true, CheckTemplateRow.class);
    }

    @Transactional
    public CheckTemplateRow updateTemplate(long id, CheckTemplateBody body) throws Exception {
        adminService.requireAdmin();
        rec("check_list_template", id, "Шаблон чек-листа не найден");
        update("check_list_template", id, templateColumns(body));
        return db.loadRec("check_list_template", id, true, CheckTemplateRow.class);
    }

    /**
     * Удалить шаблон чек-листа вместе с пунктами и нормативами.
     *
     * @throws ApiException 409 - по шаблону есть заполненные чек-листы
     */
    @Transactional
    public void deleteTemplate(long id) throws Exception {
        adminService.requireAdmin();
        rec("check_list_template", id, "Шаблон чек-листа не найден");
        try {
            Map<String, Object> params = Map.of("template", id);
            db.execSql(CheckAdminSql.deleteTemplateNormValues(), params);
            db.execSql(CheckAdminSql.deleteTemplateNorms(), params);
            db.deleteRec("check_list_template_item", Map.of("check_list_template", id));
            db.deleteRec("check_list_template", id);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    // --- пункты

    public List<CheckItemRow> items(long template) throws Exception {
        adminService.requireAdmin();
        rec("check_list_template", template, "Шаблон чек-листа не найден");
        return sortedItems(db.loadList("check_list_template_item", Map.of("check_list_template", template),
                CheckItemRow.class));
    }

    @Transactional
    public CheckItemRow createItem(long template, CheckItemBody body) throws Exception {
        adminService.requireAdmin();
        rec("check_list_template", template, "Шаблон чек-листа не найден");
        Map<String, Object> columns = itemColumns(template, null, body);
        columns.put("check_list_template", template);
        long id = insert("check_list_template_item", columns);
        return db.loadRec("check_list_template_item", id, true, CheckItemRow.class);
    }

    @Transactional
    public CheckItemRow updateItem(long id, CheckItemBody body) throws Exception {
        adminService.requireAdmin();
        DbRec item = rec("check_list_template_item", id, "Пункт не найден");
        update("check_list_template_item", id, itemColumns(item.getLong("check_list_template"), id, body));
        return db.loadRec("check_list_template_item", id, true, CheckItemRow.class);
    }

    /**
     * Удалить пункт вместе с нормативами.
     *
     * @throws ApiException 400 - в разделе есть пункты; 409 - пункт есть в заполненных чек-листах
     */
    @Transactional
    public void deleteItem(long id) throws Exception {
        adminService.requireAdmin();
        rec("check_list_template_item", id, "Пункт не найден");
        if (!db.loadList("check_list_template_item", Map.of("parent_item", id)).isEmpty()) {
            throw ApiException.badRequest("Сначала удалите пункты раздела");
        }
        try {
            db.execSql(CheckAdminSql.deleteItemNormValues(), Map.of("item", id));
            db.deleteRec("check_list_item_norm", Map.of("check_list_template_item", id));
            db.deleteRec("check_list_template_item", id);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    // --- нормативы

    /**
     * Текущие (незакрытые) нормативы пункта с допустимыми вариантами ответа.
     */
    public List<CheckNormRow> norms(long item) throws Exception {
        adminService.requireAdmin();
        rec("check_list_template_item", item, "Пункт не найден");
        Map<String, Object> key = new HashMap<>();
        key.put("check_list_template_item", item);
        key.put("dend", null);
        return withValues(db.loadList("check_list_item_norm", key, CheckNormRow.class));
    }

    @Transactional
    public CheckNormRow createNorm(long item, CheckNormBody body) throws Exception {
        adminService.requireAdmin();
        DbRec itemRec = rec("check_list_template_item", item, "Пункт не найден");
        checkNorm(itemRec, null, body);
        return norm(insertNorm(item, body, OffsetDateTime.now()));
    }

    /**
     * Исправить норматив: по нормативу проверены значения - Система закрывает его и заводит новый с того же момента.
     *
     * @return норматив после правки - тот же или новый
     */
    @Transactional
    public CheckNormRow updateNorm(long id, CheckNormBody body) throws Exception {
        adminService.requireAdmin();
        DbRec norm = currentNorm(id);
        long item = norm.getLong("check_list_template_item");
        checkNorm(rec("check_list_template_item", item, "Пункт не найден"), id, body);
        if (used(id)) {
            OffsetDateTime now = OffsetDateTime.now();
            db.updateRec("check_list_item_norm", Map.of("id", id, "dend", now));
            return norm(insertNorm(item, body, now));
        }
        Map<String, Object> columns = normColumns(body);
        columns.put("id", id);
        db.updateRec("check_list_item_norm", columns);
        db.deleteRec("check_list_item_norm_value", Map.of("check_list_item_norm", id));
        insertNormValues(id, body.checkDictValues());
        return norm(id);
    }

    /**
     * Удалить норматив: по нормативу проверены значения - Система закрывает его.
     */
    @Transactional
    public void deleteNorm(long id) throws Exception {
        adminService.requireAdmin();
        currentNorm(id);
        if (used(id)) {
            db.updateRec("check_list_item_norm", Map.of("id", id, "dend", OffsetDateTime.now()));
            return;
        }
        db.deleteRec("check_list_item_norm_value", Map.of("check_list_item_norm", id));
        db.deleteRec("check_list_item_norm", id);
    }

    // --- внутреннее

    static List<CheckDictValueRow> sortedValues(List<CheckDictValueRow> values) {
        List<CheckDictValueRow> res = new ArrayList<>(values);
        res.sort(Comparator.comparingInt(CheckDictValueRow::getSortOrder)
                           .thenComparing(CheckDictValueRow::getTextRu));
        return res;
    }

    static List<CheckTemplateRow> sortedTemplates(List<CheckTemplateRow> templates) {
        List<CheckTemplateRow> res = new ArrayList<>(templates);
        res.sort(Comparator.comparingInt(CheckTemplateRow::getSortOrder).thenComparing(CheckTemplateRow::getTextRu));
        return res;
    }

    static <T extends CheckItemRow> List<T> sortedItems(List<T> items) {
        List<T> res = new ArrayList<>(items);
        res.sort(Comparator.comparingInt(CheckItemRow::getSortOrder).thenComparing(CheckItemRow::getTextRu));
        return res;
    }

    private DbRec rec(String table, long id, String notFound) throws Exception {
        DbRec rec = db.loadRec(table, id);
        if (rec == null) {
            throw ApiException.notFound(notFound);
        }
        return rec;
    }

    private long insert(String table, Map<String, Object> columns) throws Exception {
        try {
            return db.insertRec(table, columns);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    private void update(String table, long id, Map<String, Object> columns) throws Exception {
        columns.put("id", id);
        try {
            db.updateRec(table, columns);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    private void delete(String table, long id) throws Exception {
        try {
            db.deleteRec(table, id);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    private static Map<String, Object> templateColumns(CheckTemplateBody body) {
        if (!CheckRights.CHECK_BY_OBJECT.containsKey(body.checkObject())) {
            throw ApiException.badRequest("Выберите объект проверки");
        }
        Map<String, Object> columns = Columns.of(body);
        AdminTexts.normalize(columns);
        return columns;
    }

    /**
     * Колонки пункта.
     *
     * @param id пункт; null - новый пункт
     * @throws ApiException 400 - раздел не из этого шаблона или не раздел, пункт попадает в свой подраздел, у вида
     *                      значения нет шаблона ответов, раздел с пунктами получает вид значения
     */
    private Map<String, Object> itemColumns(long template, Long id, CheckItemBody body) throws Exception {
        Integer kind = body.valueKind();
        if (kind != null && kind != CheckValueKind.STRING && kind != CheckValueKind.NUMBER
            && kind != CheckValueKind.DICT_VALUE) {
            throw ApiException.badRequest("Неизвестный вид значения");
        }
        Long dict = kind != null && kind == CheckValueKind.DICT_VALUE ? body.checkDict() : null;
        if (kind != null && kind == CheckValueKind.DICT_VALUE
            && (dict == null || db.loadRec("check_dict", dict) == null)) {
            throw ApiException.badRequest("Выберите шаблон ответов");
        }
        if (id != null && kind != null
            && !db.loadList("check_list_template_item", Map.of("parent_item", id)).isEmpty()) {
            throw ApiException.badRequest("В разделе есть пункты: раздел не может стать пунктом со значением");
        }
        for (Long parent = body.parentItem(); parent != null; ) {
            if (parent.equals(id)) {
                throw ApiException.badRequest("Пункт не может входить в свой подраздел");
            }
            DbRec p = db.loadRec("check_list_template_item", parent);
            if (p == null || p.getLong("check_list_template") != template || p.get("value_kind") != null) {
                throw ApiException.badRequest("Раздел - пункт без значения из этого же шаблона");
            }
            parent = p.getLong("parent_item");
        }
        Map<String, Object> columns = Columns.of(body);
        AdminTexts.normalize(columns);
        columns.put("check_dict", dict);
        return columns;
    }

    private DbRec currentNorm(long id) throws Exception {
        DbRec norm = rec("check_list_item_norm", id, "Норматив не найден");
        if (norm.get("dend") != null) {
            throw ApiException.badRequest("Норматив закрыт: его нельзя изменить");
        }
        return norm;
    }

    private boolean used(long id) throws Exception {
        return db.loadSqlRec(CheckAdminSql.checkNormUsed(), Map.of("id", id)).getBoolean("used");
    }

    /**
     * @param id норматив; null - новый норматив
     * @throws ApiException 400 - у пункта уже есть текущий норматив для этого типа вагона, границы перепутаны,
     *                      варианты ответа не из шаблона ответов пункта, у раздела нет нормативов
     */
    private void checkNorm(DbRec item, Long id, CheckNormBody body) throws Exception {
        if (item.get("value_kind") == null) {
            throw ApiException.badRequest("У раздела нет нормативов");
        }
        Map<String, Object> key = new HashMap<>();
        key.put("check_list_template_item", item.getLong("id"));
        key.put("dend", null);
        key.put("wagon_type", body.wagonType());
        for (DbRec other : db.loadList("check_list_item_norm", key)) {
            if (!Objects.equals(other.getLong("id"), id)) {
                throw ApiException.badRequest("У пункта уже есть норматив для этого типа вагона");
            }
        }
        if (body.numMin() != null && body.numMax() != null && body.numMin().compareTo(body.numMax()) > 0) {
            throw ApiException.badRequest("Нижняя граница больше верхней");
        }
        List<Long> values = body.checkDictValues() == null ? List.of() : body.checkDictValues();
        if (!values.isEmpty()) {
            Long dict = item.getLong("check_dict");
            List<DbRec> recs = db.loadList("check_dict_value", Map.of("id", values));
            if (dict == null || recs.size() != Set.copyOf(values).size()
                || recs.stream().anyMatch(r -> !dict.equals(r.getLong("check_dict")))) {
                throw ApiException.badRequest("Допустимые варианты - из шаблона ответов пункта");
            }
        }
        if (body.faultType() != null && db.loadRec("fault_type", body.faultType()) == null) {
            throw ApiException.badRequest("Неисправность не найдена");
        }
    }

    private static Map<String, Object> normColumns(CheckNormBody body) {
        Map<String, Object> columns = Columns.of(body);
        columns.remove("check_dict_values");
        return columns;
    }

    private long insertNorm(long item, CheckNormBody body, OffsetDateTime dbeg) throws Exception {
        Map<String, Object> columns = normColumns(body);
        columns.put("check_list_template_item", item);
        columns.put("dbeg", dbeg);
        long id = insert("check_list_item_norm", columns);
        insertNormValues(id, body.checkDictValues());
        return id;
    }

    private void insertNormValues(long norm, List<Long> values) throws Exception {
        if (values == null) {
            return;
        }
        for (Long value : Set.copyOf(values)) {
            db.insertRec("check_list_item_norm_value", Map.of("check_list_item_norm", norm, "check_dict_value", value));
        }
    }

    private CheckNormRow norm(long id) throws Exception {
        return withValues(List.of(db.loadRec("check_list_item_norm", id, true, CheckNormRow.class))).get(0);
    }

    /**
     * Нормативы с допустимыми вариантами ответа (check_list_item_norm_value).
     */
    List<CheckNormRow> withValues(List<CheckNormRow> norms) throws Exception {
        if (norms.isEmpty()) {
            return norms;
        }
        List<Long> ids = norms.stream().map(CheckNormRow::getId).toList();
        Map<Long, List<DbRec>> values = UtDb.collectGroupBy_records(
                db.loadList("check_list_item_norm_value", Map.of("check_list_item_norm", ids)), "check_list_item_norm");
        for (CheckNormRow norm : norms) {
            norm.setCheckDictValues(values.getOrDefault(norm.getId(), List.of()).stream()
                                          .map(r -> r.getLong("check_dict_value")).toList());
        }
        return norms;
    }

}
