package kz.zhsr.app.service.admin;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.service.ApiException;
import kz.zhsr.app.service.dict.DictService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Перестановка строк администрирования кнопками «вверх» и «вниз» в колонке «Порядок показа». Строка меняется местами с
 * соседней в своей группе (например, пункт - среди пунктов своего раздела), после чего порядок группы
 * перенумеровывается: 10, 20, 30... Только для роли ADMIN.
 */
@Service
public class SortOrderService {

    /**
     * Таблица с порядком показа - колонки группы: строки переставляются среди строк с теми же значениями этих колонок.
     * Правится здесь.
     */
    private static final Map<String, List<String>> GROUPS = Map.of(
            "fault_group_type", List.of(),
            "fault_type_favorite", List.of(),
            "check_dict", List.of(),
            "check_dict_value", List.of("check_dict"),
            "check_list_template", List.of(),
            "check_list_template_item", List.of("check_list_template", "parent_item"),
            "equip_list_template", List.of(),
            "equip_list_template_item", List.of("equip_list_template", "parent_item")
    );

    private final Db db;
    private final AdminService adminService;
    private final DictService dictService;

    public SortOrderService(Db db, AdminService adminService, DictService dictService) {
        this.db = db;
        this.adminService = adminService;
        this.dictService = dictService;
    }

    /**
     * Переставить строку на одну позицию; первая строка вверх и последняя вниз не двигаются.
     *
     * @param up true - вверх, false - вниз
     * @throws ApiException 404 - таблица без порядка показа или строки нет
     */
    @Transactional
    public void move(String table, long id, boolean up) throws Exception {
        adminService.requireAdmin();
        List<String> group = GROUPS.get(table);
        if (group == null) {
            throw ApiException.notFound("У таблицы " + table + " нет порядка показа");
        }
        DbRec rec = db.loadRec(table, id);
        if (rec == null) {
            throw ApiException.notFound("Запись не найдена");
        }
        Map<String, Object> key = new HashMap<>();
        for (String column : group) {
            key.put(column, rec.get(column));
        }
        List<DbRec> rows = new ArrayList<>(db.loadList(table, key));
        rows.sort(Comparator.comparingInt((DbRec r) -> r.getInt(AdminTables.SORT_ORDER))
                            .thenComparingLong(r -> r.getLong("id")));
        int index = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getLong("id") == id) {
                index = i;
            }
        }
        int other = up ? index - 1 : index + 1;
        if (other < 0 || other >= rows.size()) {
            return;
        }
        rows.set(index, rows.set(other, rows.get(index)));
        for (int i = 0; i < rows.size(); i++) {
            int order = (i + 1) * 10;
            if (rows.get(i).getInt(AdminTables.SORT_ORDER) != order) {
                db.updateRec(table, Map.of("id", rows.get(i).getLong("id"), AdminTables.SORT_ORDER, order));
            }
        }
        String dict = AdminTables.dictName(table);
        if (dict != null) {
            dictService.changed(dict);
        }
    }

}
