package kz.zhsr.app.service.equip;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.constants.EquipBasis;
import kz.zhsr.app.constants.EquipQuantityKind;
import kz.zhsr.app.dto.equip.EquipItemBody;
import kz.zhsr.app.dto.equip.EquipItemRow;
import kz.zhsr.app.dto.equip.EquipNormBody;
import kz.zhsr.app.dto.equip.EquipNormRow;
import kz.zhsr.app.dto.equip.EquipTemplateBody;
import kz.zhsr.app.dto.equip.EquipTemplateRow;
import kz.zhsr.app.service.ApiException;
import kz.zhsr.app.service.Columns;
import kz.zhsr.app.service.DbErrors;
import kz.zhsr.app.service.admin.AdminService;
import kz.zhsr.app.service.check.AdminTexts;
import kz.zhsr.app.service.check.CheckAdminSql;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Администрирование описей экипировки: шаблоны, пункты и нормативы (Ф-ЭКП-01). Только для роли ADMIN. Правила
 * удаления, отключения и правки норматива - как у чек-листа ({@link kz.zhsr.app.service.check.CheckAdminService}).
 */
@Service
public class EquipAdminService {

    private final Db db;
    private final AdminService adminService;

    public EquipAdminService(Db db, AdminService adminService) {
        this.db = db;
        this.adminService = adminService;
    }

    // --- шаблоны

    public List<EquipTemplateRow> templates() throws Exception {
        adminService.requireAdmin();
        return sortedTemplates(db.loadList("equip_list_template", Map.of(), EquipTemplateRow.class));
    }

    @Transactional
    public EquipTemplateRow createTemplate(EquipTemplateBody body) throws Exception {
        adminService.requireAdmin();
        long id = insert("equip_list_template", templateColumns(body));
        return db.loadRec("equip_list_template", id, true, EquipTemplateRow.class);
    }

    @Transactional
    public EquipTemplateRow updateTemplate(long id, EquipTemplateBody body) throws Exception {
        adminService.requireAdmin();
        rec("equip_list_template", id, "Шаблон описи не найден");
        update("equip_list_template", id, templateColumns(body));
        return db.loadRec("equip_list_template", id, true, EquipTemplateRow.class);
    }

    /**
     * Удалить шаблон описи вместе с пунктами и нормативами.
     *
     * @throws ApiException 409 - по шаблону есть заполненные описи
     */
    @Transactional
    public void deleteTemplate(long id) throws Exception {
        adminService.requireAdmin();
        rec("equip_list_template", id, "Шаблон описи не найден");
        try {
            db.execSql(CheckAdminSql.deleteEquipTemplateNorms(), Map.of("template", id));
            db.deleteRec("equip_list_template_item", Map.of("equip_list_template", id));
            db.deleteRec("equip_list_template", id);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    // --- пункты

    public List<EquipItemRow> items(long template) throws Exception {
        adminService.requireAdmin();
        rec("equip_list_template", template, "Шаблон описи не найден");
        return sortedItems(db.loadList("equip_list_template_item", Map.of("equip_list_template", template),
                EquipItemRow.class));
    }

    @Transactional
    public EquipItemRow createItem(long template, EquipItemBody body) throws Exception {
        adminService.requireAdmin();
        rec("equip_list_template", template, "Шаблон описи не найден");
        Map<String, Object> columns = itemColumns(template, null, body);
        columns.put("equip_list_template", template);
        long id = insert("equip_list_template_item", columns);
        return db.loadRec("equip_list_template_item", id, true, EquipItemRow.class);
    }

    @Transactional
    public EquipItemRow updateItem(long id, EquipItemBody body) throws Exception {
        adminService.requireAdmin();
        DbRec item = rec("equip_list_template_item", id, "Пункт не найден");
        update("equip_list_template_item", id, itemColumns(item.getLong("equip_list_template"), id, body));
        return db.loadRec("equip_list_template_item", id, true, EquipItemRow.class);
    }

    /**
     * Удалить пункт вместе с нормативами.
     *
     * @throws ApiException 400 - в разделе есть пункты; 409 - пункт есть в заполненных описях
     */
    @Transactional
    public void deleteItem(long id) throws Exception {
        adminService.requireAdmin();
        rec("equip_list_template_item", id, "Пункт не найден");
        if (!db.loadList("equip_list_template_item", Map.of("parent_item", id)).isEmpty()) {
            throw ApiException.badRequest("Сначала удалите пункты раздела");
        }
        try {
            db.deleteRec("equip_list_item_norm", Map.of("equip_list_template_item", id));
            db.deleteRec("equip_list_template_item", id);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    // --- нормативы

    /**
     * Текущие (незакрытые) нормативы позиции.
     */
    public List<EquipNormRow> norms(long item) throws Exception {
        adminService.requireAdmin();
        rec("equip_list_template_item", item, "Пункт не найден");
        Map<String, Object> key = new HashMap<>();
        key.put("equip_list_template_item", item);
        key.put("dend", null);
        return db.loadList("equip_list_item_norm", key, EquipNormRow.class);
    }

    @Transactional
    public EquipNormRow createNorm(long item, EquipNormBody body) throws Exception {
        adminService.requireAdmin();
        checkNorm(rec("equip_list_template_item", item, "Пункт не найден"), null, body);
        return norm(insertNorm(item, body, OffsetDateTime.now()));
    }

    /**
     * Исправить норматив: по нормативу посчитаны значения описей - Система закрывает его и заводит новый с того же
     * момента.
     */
    @Transactional
    public EquipNormRow updateNorm(long id, EquipNormBody body) throws Exception {
        adminService.requireAdmin();
        DbRec norm = currentNorm(id);
        long item = norm.getLong("equip_list_template_item");
        checkNorm(rec("equip_list_template_item", item, "Пункт не найден"), id, body);
        if (used(id)) {
            OffsetDateTime now = OffsetDateTime.now();
            db.updateRec("equip_list_item_norm", Map.of("id", id, "dend", now));
            return norm(insertNorm(item, body, now));
        }
        Map<String, Object> columns = normColumns(body);
        columns.put("id", id);
        update("equip_list_item_norm", id, columns);
        return norm(id);
    }

    /**
     * Удалить норматив: по нормативу посчитаны значения описей - Система закрывает его.
     */
    @Transactional
    public void deleteNorm(long id) throws Exception {
        adminService.requireAdmin();
        currentNorm(id);
        if (used(id)) {
            db.updateRec("equip_list_item_norm", Map.of("id", id, "dend", OffsetDateTime.now()));
            return;
        }
        db.deleteRec("equip_list_item_norm", id);
    }

    // --- внутреннее

    static List<EquipTemplateRow> sortedTemplates(List<EquipTemplateRow> templates) {
        List<EquipTemplateRow> res = new ArrayList<>(templates);
        res.sort(Comparator.comparingInt(EquipTemplateRow::getSortOrder).thenComparing(EquipTemplateRow::getTextRu));
        return res;
    }

    static <T extends EquipItemRow> List<T> sortedItems(List<T> items) {
        List<T> res = new ArrayList<>(items);
        res.sort(Comparator.comparingInt(EquipItemRow::getSortOrder).thenComparingLong(EquipItemRow::getId));
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

    private Map<String, Object> templateColumns(EquipTemplateBody body) throws Exception {
        if (body.faultType() != null && db.loadRec("fault_type", body.faultType()) == null) {
            throw ApiException.badRequest("Неисправность не найдена");
        }
        Map<String, Object> columns = Columns.of(body);
        AdminTexts.normalize(columns);
        return columns;
    }

    /**
     * Колонки пункта.
     *
     * @param id пункт; null - новый пункт
     * @throws ApiException 400 - раздел не из этого шаблона или не раздел, пункт попадает в свой подраздел, раздел с
     *                      пунктами получает единицу измерения
     */
    private Map<String, Object> itemColumns(long template, Long id, EquipItemBody body) throws Exception {
        String unit = AdminTexts.trim(body.unit());
        if (id != null && unit != null
            && !db.loadList("equip_list_template_item", Map.of("parent_item", id)).isEmpty()) {
            throw ApiException.badRequest("В разделе есть пункты: раздел не может стать позицией");
        }
        for (Long parent = body.parentItem(); parent != null; ) {
            if (parent.equals(id)) {
                throw ApiException.badRequest("Пункт не может входить в свой подраздел");
            }
            DbRec p = db.loadRec("equip_list_template_item", parent);
            if (p == null || p.getLong("equip_list_template") != template || p.get("unit") != null) {
                throw ApiException.badRequest("Раздел - пункт без единицы измерения из этого же шаблона");
            }
            parent = p.getLong("parent_item", null);
        }
        Map<String, Object> columns = Columns.of(body);
        AdminTexts.normalize(columns);
        columns.put("unit", unit);
        return columns;
    }

    private DbRec currentNorm(long id) throws Exception {
        DbRec norm = rec("equip_list_item_norm", id, "Норматив не найден");
        if (norm.get("dend") != null) {
            throw ApiException.badRequest("Норматив закрыт: его нельзя изменить");
        }
        return norm;
    }

    private boolean used(long id) throws Exception {
        return db.loadSqlRec(CheckAdminSql.equipNormUsed(), Map.of("id", id)).getBoolean("used");
    }

    /**
     * @param id норматив; null - новый норматив
     * @throws ApiException 400 - раздел, уже есть текущий норматив этого типа вагона, неверная база, способ сравнения
     *                      или количество
     */
    private void checkNorm(DbRec item, Long id, EquipNormBody body) throws Exception {
        if (item.get("unit") == null) {
            throw ApiException.badRequest("У раздела нет нормативов");
        }
        Map<String, Object> key = new HashMap<>();
        key.put("equip_list_template_item", item.getLong("id"));
        key.put("dend", null);
        key.put("wagon_type", body.wagonType());
        for (DbRec other : db.loadList("equip_list_item_norm", key)) {
            if (!Objects.equals(other.getLong("id"), id)) {
                throw ApiException.badRequest("У позиции уже есть норматив для этого типа вагона");
            }
        }
        if (body.basis() < EquipBasis.WAGON || body.basis() > EquipBasis.WINDOW) {
            throw ApiException.badRequest("Выберите базу расчёта");
        }
        if (body.quantityKind() != EquipQuantityKind.EXACT && body.quantityKind() != EquipQuantityKind.AT_LEAST) {
            throw ApiException.badRequest("Выберите способ сравнения");
        }
        if (body.quantityPerBasis() == null || body.quantityPerBasis().signum() < 0) {
            throw ApiException.badRequest("Укажите количество на единицу базы, не меньше нуля");
        }
    }

    private static Map<String, Object> normColumns(EquipNormBody body) {
        Map<String, Object> columns = Columns.of(body);
        columns.put("head_tail_extra", body.headTailExtra() == null ? BigDecimal.ZERO : body.headTailExtra());
        columns.put("norm_text", AdminTexts.trim(body.normText()));
        return columns;
    }

    private long insertNorm(long item, EquipNormBody body, OffsetDateTime dbeg) throws Exception {
        Map<String, Object> columns = normColumns(body);
        columns.put("equip_list_template_item", item);
        columns.put("dbeg", dbeg);
        return insert("equip_list_item_norm", columns);
    }

    private EquipNormRow norm(long id) throws Exception {
        return db.loadRec("equip_list_item_norm", id, true, EquipNormRow.class);
    }

}
