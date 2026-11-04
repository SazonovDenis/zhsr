package kz.zhsr.app.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.service.ApiException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Текущий сотрудник запроса: кто вошёл, его роли, депо и текущий рейс.
 * <p>
 * Сотрудник хранится principal'ом {@link CurrentUser} в SecurityContext потока; между запросами SecurityContext
 * лежит в сеансе (HttpSession). На каждый запрос {@link CurrentUserFilter} сверяет сотрудника с кэшем: отключённый
 * сотрудник теряет сеанс, новые роли, депо и текущий рейс действуют сразу после сброса кэша ({@link #evict}) или не позже
 * {@link #CACHE_TTL}.
 * <p>
 * Фоновые задачи и обработчики WebSocket выполняются от имени сотрудника через {@link #runAs}.
 */
@Service
public class CurrentUserService {

    /**
     * Сколько сотрудник живёт в кэше: столько может действовать старый набор ролей, если кэш не сброшен.
     */
    public static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final Db db;

    // usr.id -> сотрудник; отключённых и несуществующих в кэше нет
    private final Cache<Long, CurrentUser> cache = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL)
            .maximumSize(10_000)
            .build();

    public CurrentUserService(Db db) {
        this.db = db;
    }

    /**
     * @return текущий сотрудник; нет входа - ошибка 401
     */
    public CurrentUser get() {
        CurrentUser user = find();
        if (user == null) {
            throw ApiException.unauthorized("Требуется вход");
        }
        return user;
    }

    /**
     * @return текущий сотрудник; null - нет входа
     */
    public CurrentUser find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CurrentUser user ? user : null;
    }

    /**
     * Сделать сотрудника текущим в потоке: новый SecurityContext с его Authentication.
     *
     * @return SecurityContext с сотрудником - его сохраняют в сеанс при входе
     * @throws ApiException 401 - сотрудника нет или он отключён
     */
    public SecurityContext set(long usrId) {
        CurrentUser user = load(usrId);
        if (user == null) {
            throw ApiException.unauthorized("Учётная запись отключена");
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication(user));
        SecurityContextHolder.setContext(context);
        return context;
    }

    /**
     * Убрать сотрудника из потока. Кэш не трогает: сотрудник остаётся в кэше для следующих запросов.
     */
    public void clear() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Выполнить action от имени сотрудника и вернуть прежнего текущего сотрудника потока.
     */
    public <T> T runAs(long usrId, Callable<T> action) throws Exception {
        SecurityContext prev = SecurityContextHolder.getContext();
        try {
            set(usrId);
            return action.call();
        } finally {
            SecurityContextHolder.setContext(prev);
        }
    }

    /**
     * Сотрудник из кэша или из БД.
     *
     * @return null - сотрудника нет или он отключён (usr.disabled)
     */
    public CurrentUser load(long usrId) {
        return cache.get(usrId, this::loadFromDb);
    }

    /**
     * Сбросить сотрудника из кэша: после изменения его ролей, депо или отключения.
     */
    public void evict(long usrId) {
        cache.invalidate(usrId);
    }

    static Authentication authentication(CurrentUser user) {
        List<GrantedAuthority> authorities = user.roles().stream()
                .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r.id()))
                .toList();
        return new UsernamePasswordAuthenticationToken(user, null, authorities);
    }

    private CurrentUser loadFromDb(long usrId) {
        try {
            DbRec usr = db.loadRec("usr", usrId);
            if (usr == null || usr.getBoolean("disabled")) {
                return null;
            }
            Map<String, Object> byUsr = Map.of("usr", usrId);
            List<CurrentUser.Role> roles = db.loadSql(CurrentUserSql.roles(), byUsr, CurrentUser.Role.class);
            DbRec depot = db.loadSqlRec(CurrentUserSql.currentDepot(), byUsr);
            return new CurrentUser(usrId, usr.getString("login"), usr.getString("full_name"), roles,
                    depot == null ? null : depot.getLong("id"), depot == null ? null : depot.getLong("station", null),
                    loadTrip(usrId), usr.getBoolean("change_password"));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось загрузить сотрудника " + usrId, e);
        }
    }

    /**
     * Текущий рейс сотрудника: рейс, в бригаде которого он состоит, - выполняется; если такого нет - ближайший
     * по плановому отправлению из запланированных. У сотрудника на рейсе одна строка бригады: вагоны проводника - из неё.
     */
    private CurrentUser.Trip loadTrip(long usrId) throws Exception {
        DbRec trip = db.loadSqlRec(CurrentUserSql.currentTrip(),
                Map.of("usr", usrId, "inProgress", TripStatus.IN_PROGRESS, "planned", TripStatus.PLANNED));
        if (trip == null) {
            return null;
        }
        List<Long> wagons = new ArrayList<>();
        List<String> numbers = new ArrayList<>();
        Long wagon = trip.getLong("wagon", null);
        if (wagon != null) {
            wagons.add(wagon);
            numbers.add(trip.getString("number"));
        }
        Long wagon2 = trip.getLong("wagon2", null);
        if (wagon2 != null) {
            wagons.add(wagon2);
            numbers.add(trip.getString("number2"));
        }
        return new CurrentUser.Trip(trip.getLong("id"), trip.getString("train_number"), trip.getString("name"),
                trip.getLong("trip_status_type"), trip.getDateTimeTZ("departure"), trip.getDateTimeTZ("arrival"),
                trip.getLong("crew_duty_type"), wagons, numbers);
    }

}
