package kz.zhsr.app.service.admin;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.dto.AdminPage;
import kz.zhsr.app.dto.AdminQuery;
import kz.zhsr.app.dto.AdminUserDraft;
import kz.zhsr.app.dto.AdminUserRow;
import kz.zhsr.app.service.ApiException;
import kz.zhsr.app.service.DbErrors;
import kz.zhsr.app.service.PasswordPolicy;
import kz.zhsr.app.service.SqlQuery;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Редактор сотрудников в администрировании: сотрудник, его роли, депо и категория проводника. Только для роли ADMIN.
 * <p>
 * Сотрудник не удаляется, а отключается (usr.disabled). Депо и категория - периоды (usr_depot, usr_category): другое
 * значение закрывает текущий (незакрытый) период и открывает новый с текущего момента. Пароль, заданный администратором,
 * сотрудник меняет при входе (usr.change_password).
 */
@Service
public class AdminUserService {

    private static final Pattern PHONE = Pattern.compile("^\\+7[0-9]{10}$");

    private final Db db;
    private final AdminService adminService;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(Db db, AdminService adminService, CurrentUserService currentUserService,
                            PasswordEncoder passwordEncoder) {
        this.db = db;
        this.adminService = adminService;
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Сотрудники: поиск по логину, ФИО и телефону; по умолчанию - по ФИО.
     */
    public AdminPage<AdminUserRow> page(AdminQuery query) throws Exception {
        adminService.requireAdmin();
        int size = query.sizeOrDefault();
        if (size > AdminQuery.MAX_SIZE) {
            throw ApiException.badRequest("На странице - не больше " + AdminQuery.MAX_SIZE + " строк");
        }
        String orderBy = AdminUserSql.SORT.get(query.sort() == null || query.sort().isBlank() ? "fullName" : query.sort());
        if (orderBy == null) {
            throw ApiException.badRequest("Сортировка по полю '" + query.sort() + "' не поддерживается");
        }
        SqlQuery count = AdminUserSql.count(query.search());
        long total = db.loadSqlRec(count.sql(), count.params()).getLong("total");
        SqlQuery page = AdminUserSql.page(query.search(), orderBy, query.desc(), size,
                query.pageOrZero() * size);
        return new AdminPage<>(total, withRoles(db.loadSql(page.sql(), page.params())));
    }

    public AdminUserRow get(long id) throws Exception {
        adminService.requireAdmin();
        return row(id);
    }

    @Transactional
    public AdminUserRow create(AdminUserDraft draft) throws Exception {
        adminService.requireAdmin();
        Map<String, Object> values = usrValues(draft);
        values.put("password_hash", passwordEncoder.encode(password(draft.password(), true)));
        values.put("change_password", true);
        long id;
        try {
            id = db.insertRec("usr", values);
            setRoles(id, draft.roles());
            setPeriod(id, "usr_depot", "depot", draft.depot());
            setPeriod(id, "usr_category", "category_type", draft.categoryType());
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        return row(id);
    }

    @Transactional
    public AdminUserRow update(long id, AdminUserDraft draft) throws Exception {
        adminService.requireAdmin();
        row(id);
        if (id == currentUserService.get().id() && (draft.disabled() || !draft.roles().contains(RoleType.ADMIN))) {
            throw ApiException.badRequest("Нельзя отключить себя или снять с себя роль «Администратор компании»");
        }
        Map<String, Object> values = usrValues(draft);
        values.put("id", id);
        String password = password(draft.password(), false);
        if (password != null) {
            values.put("password_hash", passwordEncoder.encode(password));
            values.put("change_password", true);
        }
        try {
            db.updateRec("usr", values);
            setRoles(id, draft.roles());
            setPeriod(id, "usr_depot", "depot", draft.depot());
            setPeriod(id, "usr_category", "category_type", draft.categoryType());
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        currentUserService.evict(id);
        return row(id);
    }

    // --- внутреннее

    private AdminUserRow row(long id) throws Exception {
        DbRec rec = db.loadSqlRec(AdminUserSql.row(), Map.of("id", id));
        if (rec == null) {
            throw ApiException.notFound("Сотрудник не найден");
        }
        return withRoles(List.of(rec)).get(0);
    }

    private List<AdminUserRow> withRoles(List<DbRec> recs) throws Exception {
        if (recs.isEmpty()) {
            return List.of();
        }
        List<Long> ids = recs.stream().map(r -> r.getLong("id")).toList();
        Map<Long, List<Long>> roles = new HashMap<>();
        for (DbRec r : db.loadList("usr_role", Map.of("usr", ids))) {
            roles.computeIfAbsent(r.getLong("usr"), k -> new ArrayList<>()).add(r.getLong("role_type"));
        }
        List<AdminUserRow> res = new ArrayList<>(recs.size());
        for (DbRec r : recs) {
            long id = r.getLong("id");
            List<Long> userRoles = roles.getOrDefault(id, new ArrayList<>());
            userRoles.sort(null);
            res.add(new AdminUserRow(id, r.getString("login"), r.getString("full_name"), r.getString("phone"),
                    userRoles, r.getLong("depot", null), r.getLong("category_type", null), r.getBoolean("disabled"),
                    r.getBoolean("change_password")));
        }
        return res;
    }

    /**
     * Поля usr из формы: логин, ФИО, телефон, отключён. Пустой логин или ФИО, неверный телефон - ошибка 400.
     */
    private static Map<String, Object> usrValues(AdminUserDraft draft) {
        String login = draft.login() == null ? "" : draft.login().trim();
        String fullName = draft.fullName() == null ? "" : draft.fullName().trim().replaceAll("\\s+", " ");
        String phone = draft.phone() == null || draft.phone().isBlank() ? null : draft.phone().trim();
        if (login.isEmpty() || login.length() > 50) {
            throw ApiException.badRequest("Логин - от 1 до 50 символов");
        }
        if (fullName.isEmpty() || fullName.length() > 200) {
            throw ApiException.badRequest("ФИО - от 1 до 200 символов");
        }
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw ApiException.badRequest("Телефон - в виде +7XXXXXXXXXX");
        }
        Map<String, Object> values = new HashMap<>();
        values.put("login", login);
        values.put("full_name", fullName);
        values.put("phone", phone);
        values.put("disabled", draft.disabled());
        return values;
    }

    /**
     * @return пароль из формы, проверенный {@link PasswordPolicy#check}; null - пароль не задан и не обязателен
     */
    private static String password(String password, boolean required) {
        if (!required && (password == null || password.isEmpty())) {
            return null;
        }
        PasswordPolicy.check(password);
        return password;
    }

    /**
     * Роли сотрудника - ровно roles: лишние удаляются, недостающие добавляются.
     */
    private void setRoles(long usr, List<Long> roles) throws Exception {
        Set<Long> wanted = new LinkedHashSet<>(roles);
        for (DbRec r : db.loadList("usr_role", Map.of("usr", usr))) {
            if (!wanted.remove(r.getLong("role_type"))) {
                db.deleteRec("usr_role", r.getLong("id"));
            }
        }
        for (Long role : wanted) {
            db.insertRec("usr_role", Map.of("usr", usr, "role_type", role));
        }
    }

    /**
     * Период сотрудника (usr_depot, usr_category) со значением value с текущего момента: прежний период с другим
     * значением закрывается; value = null - периода больше нет.
     */
    private void setPeriod(long usr, String table, String column, Long value) throws Exception {
        Map<String, Object> open = new HashMap<>();
        open.put("usr", usr);
        open.put("dend", null);
        DbRec current = db.loadRec(table, open);
        Long currentValue = current == null ? null : current.getLong(column);
        if (Objects.equals(currentValue, value)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (current != null) {
            db.updateRec(table, Map.of("id", current.getLong("id"), "dend", now));
        }
        if (value != null) {
            db.insertRec(table, Map.of("usr", usr, column, value, "dbeg", now));
        }
    }

}
