package kz.zhsr.app.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.dto.user.ChangePasswordRequest;
import kz.zhsr.app.dto.user.CurrentState;
import kz.zhsr.app.dto.user.LoginRequest;
import kz.zhsr.app.dto.user.UserInfo;
import kz.zhsr.app.dto.user.UserTrip;
import kz.zhsr.app.service.dict.DictService;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Сеанс сотрудника: вход, выход, кто вошёл и сводка для него (zhsr-web: api/session.ts, api/state.ts).
 */
@Service
public class SessionService {

    private final Db db;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final DictService dictService;
    private final SecurityContextRepository securityContextRepository;

    public SessionService(Db db, PasswordEncoder passwordEncoder, CurrentUserService currentUserService,
                          DictService dictService, SecurityContextRepository securityContextRepository) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.dictService = dictService;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Войти: проверить логин и пароль, открыть новый сеанс и сохранить в нём сотрудника.
     *
     * @throws ApiException 401 - неверный логин или пароль, учётная запись отключена; 406 - вход выполнен, но сотрудник
     *                     должен сменить пароль: сеанс открыт, в нём разрешена только смена пароля
     */
    public void login(LoginRequest req, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String login = req.username() == null ? "" : req.username().trim();
        DbRec usr = login.isEmpty() || req.password() == null ? null : db.loadRec("usr", Map.of("login", login));
        if (usr == null || !passwordEncoder.matches(req.password(), usr.getString("password_hash"))) {
            throw ApiException.unauthorized("Неверный логин или пароль");
        }
        // отключение проверяется после пароля: иначе по любому паролю можно узнать, что учётная запись отключена
        if (usr.getBoolean("disabled")) {
            throw ApiException.unauthorized("Учётная запись отключена");
        }

        // новый сеанс на вход - защита от фиксации сеанса
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        request.getSession(true);

        long usrId = usr.getLong("id");
        currentUserService.evict(usrId);
        SecurityContext context = currentUserService.set(usrId);
        securityContextRepository.saveContext(context, request, response);

        CurrentUser user = currentUserService.get();
        if (user.mustChangePassword()) {
            throw ApiException.passwordChangeRequired();
        }
    }

    /**
     * Сменить пароль вошедшему сотруднику и снять флаг «сменить пароль при входе».
     *
     * @throws ApiException 400 - текущий пароль неверен, новый пароль не проходит {@link PasswordPolicy#check} или
     *                      совпадает с текущим
     */
    @Transactional
    public void changePassword(ChangePasswordRequest req) throws Exception {
        CurrentUser user = currentUserService.get();
        DbRec usr = db.loadRec("usr", user.id(), true);
        if (req.oldPassword() == null || !passwordEncoder.matches(req.oldPassword(), usr.getString("password_hash"))) {
            throw ApiException.badRequest("Текущий пароль неверен");
        }
        String password = req.newPassword();
        PasswordPolicy.check(password);
        if (password.equals(req.oldPassword())) {
            throw ApiException.badRequest("Новый пароль совпадает с текущим");
        }
        db.updateRec("usr", Map.of("id", user.id(), "password_hash", passwordEncoder.encode(password),
                "change_password", false));
        currentUserService.evict(user.id());
        currentUserService.set(user.id());
    }

    /**
     * Завершить сеанс. Без сеанса - ничего не делает.
     */
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        currentUserService.clear();
    }

    /**
     * Кто вошёл и контекст сервера: идентификатор БД и версии справочников. Контекст в сеансе не хранится: версии
     * меняются во время сеанса, поэтому читаются при каждом вызове.
     */
    public UserInfo user() throws Exception {
        CurrentUser user = currentUserService.get();
        CurrentUser.Trip t = user.trip();
        return new UserInfo(
                user.id(),
                user.shortName(),
                user.firstName(),
                user.roles().stream().map(CurrentUser.Role::id).toList(),
                user.roles().stream().map(CurrentUser.Role::service).filter(s -> s != null).distinct().toList(),
                user.depot(),
                user.depotStation(),
                userTrip(t),
                userTrip(user.otherTrip()),
                dbGuid(),
                dictService.versions());
    }

    private static UserTrip userTrip(CurrentUser.Trip t) {
        return t == null ? null : new UserTrip(t.id(), t.train(), t.name(), t.state(), t.departure(), t.arrival(),
                t.duty(), t.wagonNumbers(), t.plannedDt(), t.departedDt(), t.arrivedDt(), t.departureMarkedDt(),
                t.arrivalMarkedDt());
    }

    /**
     * Сводка для сотрудника. Счётчики (заявки, акты передачи) сервер не считает: пусто.
     */
    public CurrentState state() {
        currentUserService.get();
        return new CurrentState(Map.of());
    }

    /**
     * Идентификатор серверной БД. Его пишет install/db-reset; без него фронт не может сверить локальную БД.
     */
    private String dbGuid() throws Exception {
        List<DbRec> rows = db.loadList("db_info", null);
        if (rows.size() != 1) {
            throw new IllegalStateException("В db_info должна быть одна строка: БД создаётся через install/db-reset");
        }
        return rows.get(0).getString("db_guid");
    }

}
