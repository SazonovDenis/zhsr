package kz.zhsr.app.service.admin;

import kz.zhsr.app.constants.ConductorCategory;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.ServiceType;
import kz.zhsr.app.constants.Severity;
import kz.zhsr.app.dto.admin.AdminColumn;
import kz.zhsr.app.dto.admin.AdminTable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Перечень таблиц общего редактора администрирования и их поля. Другие таблицы через него не редактируются:
 * системные справочники (kz.zhsr.app.constants) меняются только вместе с кодом, у сотрудников, вагонов и маршрутов -
 * свои редакторы.
 * <p>
 * Имена таблиц и колонок отсюда попадают в SQL как есть: значения - только константы этого класса.
 */
public final class AdminTables {

    public static final String TEXT = "TEXT";
    public static final String INT = "INT";
    public static final String DECIMAL = "DECIMAL";
    public static final String BOOL = "BOOL";
    public static final String DATE = "DATE";
    public static final String REF = "REF";

    /**
     * Поле записи «отключена» у таблиц, записи которых не удаляются.
     */
    public static final String DISABLED = "disabled";

    private static final Map<String, AdminTable> TABLES = new LinkedHashMap<>();

    /**
     * Текстовое поле записи таблицы, на которую ссылаются: им ссылка показывается в строке и в вариантах выбора.
     */
    private static final Map<String, String> TEXT_COLUMN = Map.of(
            RoleType.TABLE, "text",
            ConductorCategory.TABLE, "text",
            "station", "name",
            "depot", "name_ru",
            "fault_group_type", "name",
            "wagon_type", "text",
            "model_type", "text",
            Severity.TABLE, "text",
            ServiceType.TABLE, "text"
    );

    /**
     * Справочник фронта (DictManager) по таблице: его версия в dict_version растёт при каждом изменении таблицы.
     */
    private static final Map<String, String> DICT_NAME = Map.of(
            "depot", "Depot",
            "yard", "Yard",
            "station", "Station",
            "wagon_type", "WagonType",
            "model_type", "WagonModel",
            "fault_group_type", "FaultGroup",
            "fault_type", "FaultType"
    );

    static {
        add(new AdminTable("depot", "Депо", List.of(
                text("code", "Код", true, 20),
                text("name_ru", "Название на русском", true, 255),
                text("name_kk", "Название на казахском", false, 255),
                text("name_en", "Название на английском", false, 255),
                ref("station", "Станция", false, "station"))));
        add(new AdminTable("yard", "Парки отстоя", List.of(
                text("code", "Код", true, 20),
                text("name", "Название", true, 255),
                ref("station", "Станция", false, "station"))));
        add(new AdminTable("station", "Станции", List.of(
                text("code", "Код", true, 20),
                text("name", "Название", true, 255))));
        add(new AdminTable("stage", "Перегоны", List.of(
                // станции перегона - в любом порядке: AdminService ставит станцию с меньшим id в from_station
                ref("from_station", "Станция 1", true, "station"),
                ref("to_station", "Станция 2", true, "station"),
                col("km", "Расстояние, км", INT, true))));
        add(new AdminTable("wagon_type", "Типы вагонов", List.of(
                text("code", "Код", true, 20),
                text("text", "Название", true, 255))));
        add(new AdminTable("model_type", "Модели вагонов", List.of(
                text("code", "Код", true, 20),
                text("text", "Название", true, 255),
                // базы расчёта норм экипировки
                col("seats", "Мест", INT, false),
                col("compartments", "Купе", INT, false),
                col("windows", "Окон", INT, false))));
        add(new AdminTable("fault_group_type", "Группы неисправностей", List.of(
                text("code", "Код", true, 20),
                text("name", "Название", true, 200),
                col("sort_order", "Порядок показа", INT, true))));
        add(new AdminTable("fault_type", "Неисправности", List.of(
                text("code", "Код", true, 30),
                text("name", "Название", true, 300),
                ref("fault_group_type", "Группа", false, "fault_group_type"),
                ref("severity_type", "Критичность по умолчанию", true, Severity.TABLE),
                ref("service", "Служба-исполнитель", true, ServiceType.TABLE),
                // отключённая запись в таблице показывается зачёркнутой: своей колонки нет, поле - только в форме
                formOnly(DISABLED, "Отключена", BOOL, true))));
    }

    private AdminTables() {
    }

    /**
     * Все таблицы общего редактора по порядку меню.
     */
    public static List<AdminTable> all() {
        return List.copyOf(TABLES.values());
    }

    /**
     * @return таблица; null - таблицы нет в перечне
     */
    public static AdminTable find(String name) {
        return TABLES.get(name);
    }

    /**
     * @return текстовое поле таблицы, на которую можно ссылаться; null - на таблицу ссылаться нельзя
     */
    public static String textColumn(String table) {
        return TEXT_COLUMN.get(table);
    }

    /**
     * @return имя справочника фронта; null - таблица не справочник фронта
     */
    public static String dictName(String table) {
        return DICT_NAME.get(table);
    }

    private static void add(AdminTable table) {
        for (AdminColumn c : table.columns()) {
            if (REF.equals(c.type()) && textColumn(c.ref()) == null) {
                throw new IllegalStateException(table.name() + "." + c.name() + ": нет текстового поля у " + c.ref());
            }
        }
        TABLES.put(table.name(), table);
    }

    private static AdminColumn text(String name, String title, boolean required, int maxLength) {
        return new AdminColumn(name, title, TEXT, required, maxLength, null, true);
    }

    private static AdminColumn ref(String name, String title, boolean required, String ref) {
        return new AdminColumn(name, title, REF, required, null, ref, true);
    }

    private static AdminColumn col(String name, String title, String type, boolean required) {
        return new AdminColumn(name, title, type, required, null, null, true);
    }

    private static AdminColumn formOnly(String name, String title, String type, boolean required) {
        return new AdminColumn(name, title, type, required, null, null, false);
    }

}
