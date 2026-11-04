package kz.zhsr.app.service.admin;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.SystemDicts;
import kz.zhsr.app.dto.AdminColumn;
import kz.zhsr.app.dto.AdminOption;
import kz.zhsr.app.dto.AdminPage;
import kz.zhsr.app.dto.AdminQuery;
import kz.zhsr.app.dto.AdminTable;
import kz.zhsr.app.service.ApiException;
import kz.zhsr.app.service.DbErrors;
import kz.zhsr.app.service.SqlQuery;
import kz.zhsr.app.service.dict.DictService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Общий редактор администрирования: список со страницами и поиском, чтение, создание, изменение и удаление записей
 * таблиц из {@link AdminTables}. Только для роли ADMIN.
 * <p>
 * Запись, на которую ссылаются другие данные, не удаляется (409); её можно отключить, если у таблицы есть поле disabled.
 * После любого изменения таблицы, которая является
 * справочником фронта, растёт её версия, а фронт получает команду перезагрузить справочник ({@link DictService#changed}).
 */
@Service
public class AdminService {

    private final Db db;
    private final CurrentUserService currentUserService;
    private final DictService dictService;

    public AdminService(Db db, CurrentUserService currentUserService, DictService dictService) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.dictService = dictService;
    }

    /**
     * Таблицы общего редактора с полями.
     */
    public List<AdminTable> tables() {
        requireAdmin();
        return AdminTables.all();
    }

    public AdminPage<Map<String, Object>> page(String tableName, AdminQuery query) throws Exception {
        requireAdmin();
        AdminTable table = table(tableName);
        int size = query.sizeOrDefault();
        if (size > AdminQuery.MAX_SIZE) {
            throw ApiException.badRequest("На странице - не больше " + AdminQuery.MAX_SIZE + " строк");
        }
        String orderBy = sortColumn(table, query.sort());

        SqlQuery count = AdminSql.count(table, query.search());
        long total = db.loadSqlRec(count.sql(), count.params()).getLong("total");
        SqlQuery page = AdminSql.page(table, query.search(), orderBy, query.desc(), size, query.pageOrZero() * size);
        return new AdminPage<>(total, withRefTexts(table, db.loadSql(page.sql(), page.params())));
    }

    public Map<String, Object> get(String tableName, long id) throws Exception {
        requireAdmin();
        return row(table(tableName), id);
    }

    @Transactional
    public Map<String, Object> create(String tableName, Map<String, Object> body) throws Exception {
        requireAdmin();
        AdminTable table = table(tableName);
        Map<String, Object> values = values(table, body);
        normalize(table, values);
        long id;
        try {
            id = db.insertRec(table.name(), values);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        changed(table);
        return row(table, id);
    }

    @Transactional
    public Map<String, Object> update(String tableName, long id, Map<String, Object> body) throws Exception {
        requireAdmin();
        AdminTable table = table(tableName);
        row(table, id);
        Map<String, Object> values = values(table, body);
        normalize(table, values);
        values.put("id", id);
        try {
            db.updateRec(table.name(), values);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        changed(table);
        return row(table, id);
    }

    /**
     * @throws ApiException 409 - запись используется
     */
    @Transactional
    public void delete(String tableName, long id) throws Exception {
        requireAdmin();
        AdminTable table = table(tableName);
        row(table, id);
        try {
            db.deleteRec(table.name(), id);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        changed(table);
    }

    /**
     * Варианты для поля-ссылки: записи таблицы, на которую ссылается поле таблицы общего редактора.
     */
    public List<AdminOption> options(String refTable) throws Exception {
        requireAdmin();
        String textColumn = AdminTables.textColumn(refTable);
        if (textColumn == null) {
            throw ApiException.notFound("Нет вариантов для таблицы " + refTable);
        }
        AdminTable table = AdminTables.find(refTable);
        boolean hasDisabled = table != null
                && table.columns().stream().anyMatch(c -> AdminTables.DISABLED.equals(c.name()));
        Map<String, Object> params = new HashMap<>();
        if (hasDisabled) {
            params.put(AdminTables.DISABLED, false);
        }
        return db.loadList(refTable, params).stream()
                .map(r -> new AdminOption(r.getLong("id"), r.getString(textColumn)))
                .sorted(Comparator.comparing(AdminOption::text, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AdminOption::id))
                .toList();
    }

    /**
     * Проверить, что текущий сотрудник - администратор.
     *
     * @throws ApiException 403 - нет роли ADMIN
     */
    public void requireAdmin() {
        if (!currentUserService.get().hasRole(RoleType.ADMIN)) {
            throw ApiException.forbidden("Нужна роль «Администратор компании»");
        }
    }

    // --- внутреннее

    private static AdminTable table(String name) {
        AdminTable table = AdminTables.find(name);
        if (table == null) {
            throw ApiException.notFound("Таблица " + name + " не редактируется в администрировании");
        }
        return table;
    }

    private Map<String, Object> row(AdminTable table, long id) throws Exception {
        DbRec rec = db.loadRec(table.name(), id);
        if (rec == null) {
            throw ApiException.notFound("Запись не найдена");
        }
        return withRefTexts(table, List.of(rec)).get(0);
    }

    /**
     * Строки для фронта: поля таблицы и для каждого поля-ссылки {@code <поле>_text} - текст записи, на которую оно
     * ссылается (тексты - одним запросом на таблицу ссылки).
     */
    private List<Map<String, Object>> withRefTexts(AdminTable table, List<DbRec> recs) throws Exception {
        List<Map<String, Object>> rows = recs.stream().map(AdminService::toMap).toList();
        for (AdminColumn c : table.columns()) {
            if (!AdminTables.REF.equals(c.type())) {
                continue;
            }
            Set<Long> ids = new HashSet<>();
            for (Map<String, Object> row : rows) {
                if (row.get(c.name()) instanceof Number n) {
                    ids.add(n.longValue());
                }
            }
            Map<Long, String> texts = new HashMap<>();
            if (!ids.isEmpty()) {
                String textColumn = AdminTables.textColumn(c.ref());
                for (DbRec ref : db.loadList(c.ref(), Map.of("id", ids))) {
                    texts.put(ref.getLong("id"), ref.getString(textColumn));
                }
            }
            for (Map<String, Object> row : rows) {
                Object value = row.get(c.name());
                row.put(c.name() + "_text", value instanceof Number n ? texts.get(n.longValue()) : null);
            }
        }
        return rows;
    }

    private void changed(AdminTable table) throws Exception {
        String dict = AdminTables.dictName(table.name());
        if (dict != null) {
            dictService.changed(dict);
        }
    }

    /**
     * Правила таблицы, которых нет в описании полей. Перегон хранится один раз на пару станций, станция с меньшим id -
     * from_station (ck_stage_station_order): станции из формы, выбранные в любом порядке, ставятся по возрастанию id.
     */
    private static void normalize(AdminTable table, Map<String, Object> values) {
        if (table.name().equals("stage")) {
            long a = (Long) values.get("from_station");
            long b = (Long) values.get("to_station");
            if (a == b) {
                throw ApiException.badRequest("Станции перегона должны быть разными");
            }
            values.put("from_station", Math.min(a, b));
            values.put("to_station", Math.max(a, b));
        }
    }

    private static String sortColumn(AdminTable table, String sort) {
        if (sort == null || sort.isBlank()) {
            return table.columns().get(0).name();
        }
        for (AdminColumn c : table.columns()) {
            if (c.name().equals(sort) && c.listed()) {
                return c.name();
            }
        }
        throw ApiException.badRequest("Сортировка по полю '" + sort + "' не поддерживается");
    }

    /**
     * Значения полей записи из тела запроса: все поля таблицы, приведённые к типу колонки. Поле, которого нет в теле, -
     * пусто. Обязательное пустое поле или длинная строка - ошибка 400.
     */
    static Map<String, Object> values(AdminTable table, Map<String, Object> body) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (AdminColumn c : table.columns()) {
            Object value = convert(c, body == null ? null : body.get(c.name()));
            if (value == null && c.required()) {
                throw ApiException.badRequest("Заполните поле «" + c.title() + "»");
            }
            values.put(c.name(), value);
        }
        return values;
    }

    private static Object convert(AdminColumn c, Object raw) {
        if (raw == null || (raw instanceof String s && s.isBlank())) {
            return null;
        }
        try {
            return switch (c.type()) {
                case AdminTables.TEXT -> {
                    String s = raw.toString().trim();
                    if (c.maxLength() != null && s.length() > c.maxLength()) {
                        throw ApiException.badRequest("Поле «" + c.title() + "» - не длиннее " + c.maxLength()
                                + " символов");
                    }
                    yield s;
                }
                case AdminTables.INT -> raw instanceof Number n ? n.intValue() : Integer.parseInt(raw.toString().trim());
                case AdminTables.REF -> raw instanceof Number n ? n.longValue() : Long.parseLong(raw.toString().trim());
                case AdminTables.DECIMAL -> new BigDecimal(raw.toString().trim());
                case AdminTables.BOOL -> raw instanceof Boolean b ? b : Boolean.parseBoolean(raw.toString().trim());
                case AdminTables.DATE -> LocalDate.parse(raw.toString().trim());
                default -> throw new IllegalStateException("Неизвестный вид поля " + c.type());
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw ApiException.badRequest("Неверное значение поля «" + c.title() + "»");
        }
    }

    private static Map<String, Object> toMap(DbRec rec) {
        return new HashMap<>(rec);
    }

    static {
        // общий редактор не должен открывать системные справочники
        for (Class<?> cls : SystemDicts.CLASSES) {
            try {
                if (AdminTables.find(SystemDicts.table(cls)) != null) {
                    throw new IllegalStateException("Системный справочник в общем редакторе: " + SystemDicts.table(cls));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

}
