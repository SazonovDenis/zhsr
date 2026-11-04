package kz.zhsr.app.service.request;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RequestStatus;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.dto.ListSort;
import kz.zhsr.app.dto.RequestCounts;
import kz.zhsr.app.dto.RequestDetail;
import kz.zhsr.app.dto.RequestDraft;
import kz.zhsr.app.dto.RequestEvent;
import kz.zhsr.app.dto.RequestFilter;
import kz.zhsr.app.dto.RequestPhoto;
import kz.zhsr.app.dto.RequestRow;
import kz.zhsr.app.dto.TripRef;
import kz.zhsr.app.dto.UserRef;
import kz.zhsr.app.service.ApiException;
import kz.zhsr.app.service.SqlQuery;
import kz.zhsr.app.service.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Заявки на ремонт (Ф-ЗАЯ-01 … Ф-ЗАЯ-07): список, карточка с историей, подача, исправление, действия, назначение
 * исполнителя, пояснения и фото. Права - только через {@link RequestRights}.
 * <p>
 * Статус заявки - журнал repair_request_status, исполнитель - журнал repair_request_executor (текущая запись - dend
 * пуст), их копия - repair_request_info; смена статуса, комментарий к ней и копия - в одной транзакции. Первая запись
 * журнала статусов вносится при подаче. Записи журналов одного действия - с одним временем. Служба заявки определяется
 * типом неисправности и запоминается в repair_request_info при подаче и при смене типа исправлением заявки. Фото - в
 * хранилище файлов ({@link FileStorage}), в БД - ключ файла; фото, сохранённое в один момент с пояснением того же
 * сотрудника, показывается вместе с ним.
 * <p>
 * Отметки «передано в депо» и «ТОР» пока не хранятся: в строке заявки они всегда false.
 */
@Service
public class RequestService {

    /**
     * Код компании в номере заявки: «KZR-26-00042».
     */
    public static final String COMPANY_CODE = "KZR";

    /**
     * Ключ блокировки выдачи номеров заявок (pg_advisory_xact_lock).
     */
    private static final long NUMBER_LOCK = 3_000_001L;

    private final Db db;
    private final CurrentUserService currentUserService;
    private final RequestRights rights;
    private final FileStorage storage;

    public RequestService(Db db, CurrentUserService currentUserService, RequestRights rights, FileStorage storage) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.rights = rights;
        this.storage = storage;
    }

    // --- чтение

    /**
     * Список заявок по фильтру (Ф-ЗАЯ-06). По умолчанию - сначала новые.
     */
    public List<RequestRow> list(RequestFilter filter, ListSort sort) throws Exception {
        return load(filter, true, sort);
    }

    /**
     * Числа у фильтров статуса и критичности: по фильтру без самих этих отборов.
     */
    public RequestCounts counts(RequestFilter filter) throws Exception {
        Map<Long, Integer> statuses = new LinkedHashMap<>();
        Map<Long, Integer> severities = new LinkedHashMap<>();
        for (RequestRow r : load(filter, false, new ListSort(null, false))) {
            statuses.merge(r.getRequestStatusType(), 1, Integer::sum);
            severities.merge(r.getSeverityType(), 1, Integer::sum);
        }
        return new RequestCounts(statuses, severities);
    }

    /**
     * Карточка заявки: поля, история и фото.
     *
     * @throws ApiException 404 - заявки нет
     */
    public RequestDetail detail(long id) throws Exception {
        RequestDetail row = row(id, RequestDetail::new);

        Map<Instant, DbRec> statusAt = journalByTime("repair_request_status", id);
        Map<Instant, DbRec> executorAt = journalByTime("repair_request_executor", id);
        List<DbRec> notes = new ArrayList<>(db.loadList("repair_request_note", Map.of("repair_request", id)));
        notes.sort(Comparator.comparing(rec -> rec.getDateTimeTZ("created_dt")));
        List<DbRec> photos = db.loadList("repair_request_photo", Map.of("repair_request", id));

        Set<Long> usrs = new HashSet<>();
        for (DbRec rec : statusAt.values()) {
            addNotNull(usrs, rec.getLong("created_by_usr", null));
        }
        for (DbRec rec : executorAt.values()) {
            addNotNull(usrs, rec.getLong("executor_usr", null));
            addNotNull(usrs, rec.getLong("created_by_usr", null));
        }
        for (DbRec rec : notes) {
            usrs.add(rec.getLong("created_by_usr"));
        }

        // фото группами: один сотрудник в один момент; группа в момент подачи - фото заявки
        Map<String, List<RequestPhoto>> photoGroups = new LinkedHashMap<>();
        Map<String, OffsetDateTime> groupTime = new HashMap<>();
        Map<String, Long> groupUsr = new HashMap<>();
        List<RequestPhoto> requestPhotos = new ArrayList<>();
        photos.sort(Comparator.comparing((DbRec rec) -> rec.getDateTimeTZ("created_dt"))
                .thenComparing(rec -> rec.getLong("id")));
        for (DbRec rec : photos) {
            RequestPhoto photo = new RequestPhoto(rec.getLong("id"));
            OffsetDateTime at = rec.getDateTimeTZ("created_dt");
            long usr = rec.getLong("created_by_usr");
            if (usr == row.getCreatedByUsr().id() && at.isEqual(row.getCreatedDt())) {
                requestPhotos.add(photo);
                continue;
            }
            String key = groupKey(usr, at);
            photoGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(photo);
            groupTime.put(key, at);
            groupUsr.put(key, usr);
            usrs.add(usr);
        }
        Map<Long, UserRef> users = users(usrs);

        List<RequestEvent> events = new ArrayList<>();
        events.add(new RequestEvent(row.getCreatedDt(), row.getCreatedByUsr(), "CREATED", null, null, null, List.of()));

        // комментарий к смене статуса - пояснение того же сотрудника в тот же момент (пишутся одной транзакцией)
        Set<Long> usedNotes = new HashSet<>();
        long prevStatus = RequestStatus.NEW;
        Long prevExecutor = null;
        Set<Instant> moments = new TreeSet<>(statusAt.keySet());
        moments.addAll(executorAt.keySet());
        for (Instant moment : moments) {
            DbRec statusRec = statusAt.get(moment);
            DbRec executorRec = executorAt.get(moment);
            // запись журнала статусов при подаче: событие подачи уже есть; исполнитель при подаче - событие назначения
            if (executorRec == null && moment.equals(row.getCreatedDt().toInstant())) {
                continue;
            }
            DbRec rec = statusRec != null ? statusRec : executorRec;
            OffsetDateTime at = rec.getDateTimeTZ("dbeg");
            Long by = rec.getLong("created_by_usr", null);
            long status = statusRec != null ? statusRec.getLong("request_status_type") : prevStatus;
            Long executor = executorRec != null ? executorRec.getLong("executor_usr", null) : prevExecutor;
            DbRec note = null;
            for (DbRec n : notes) {
                if (!usedNotes.contains(n.getLong("id")) && Objects.equals(n.getLong("created_by_usr"), by)
                        && n.getDateTimeTZ("created_dt").isEqual(at)) {
                    note = n;
                    break;
                }
            }
            UserRef byRef = by == null ? null : users.get(by);
            UserRef executorRef = executor == null ? null : users.get(executor);
            String action = historyAction(prevStatus, prevExecutor, status, executor, note != null);
            if (action == null) {
                events.add(new RequestEvent(at, byRef, "ASSIGN", null, executorRef, null, List.of()));
            } else {
                String text = note == null ? null : note.getString("note");
                events.add(new RequestEvent(at, byRef, "STATUS", action, null, text, List.of()));
            }
            if (note != null) {
                usedNotes.add(note.getLong("id"));
            }
            prevStatus = status;
            prevExecutor = executor;
        }
        // пояснение и фото того же сотрудника в тот же момент - одной записью; иначе - отдельно
        for (DbRec n : notes) {
            if (!usedNotes.contains(n.getLong("id"))) {
                String key = groupKey(n.getLong("created_by_usr"), n.getDateTimeTZ("created_dt"));
                List<RequestPhoto> list = photoGroups.remove(key);
                events.add(new RequestEvent(n.getDateTimeTZ("created_dt"), users.get(n.getLong("created_by_usr")), "NOTE", null,
                        null, n.getString("note"), list == null ? List.of() : list));
            }
        }
        for (Map.Entry<String, List<RequestPhoto>> g : photoGroups.entrySet()) {
            events.add(new RequestEvent(groupTime.get(g.getKey()), users.get(groupUsr.get(g.getKey())), "NOTE", null,
                    null, null, g.getValue()));
        }
        // подача - первой; остальное - по времени
        events.subList(1, events.size()).sort(Comparator.comparing(RequestEvent::at));

        row.setEvents(events);
        row.setPhotos(requestPhotos);
        return row;
    }

    /**
     * Сотрудники службы - кому можно назначить заявку.
     */
    public List<UserRef> serviceStaff(long service) throws Exception {
        return db.loadSql(RequestSql.serviceStaff(), Map.of("service", service)).stream()
                .map(rec -> new UserRef(rec.getLong("id"), CurrentUser.shortName(rec.getString("full_name"))))
                .toList();
    }

    /**
     * Файл фото заявки.
     *
     * @throws ApiException 404 - фото нет
     */
    public FileStorage.StoredFile photo(long id) throws Exception {
        DbRec photo = db.loadRec("repair_request_photo", id);
        if (photo == null) {
            throw ApiException.notFound("Фото не найдено");
        }
        return storage.get(photo.getString("storage_key"));
    }

    // --- изменение

    /**
     * Подать заявку (Ф-ЗАЯ-01): Система присваивает номер; заявка - «новая», а если мастер депо задал исполнителя -
     * «назначена».
     */
    @Transactional
    public RequestRow create(RequestDraft draft, List<MultipartFile> photos) throws Exception {
        CurrentUser user = currentUserService.get();
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> fields = draftFields(draft);
        long service = faultService(draft.faultType());
        if (draft.executorUsr() != null) {
            rights.checkAssignOnCreate(user);
            checkStaff(service, draft.executorUsr());
        }

        fields.put("number", nextNumber(now));
        fields.put("created_by_usr", user.id());
        fields.put("created_dt", now);
        long id = db.insertRec("repair_request", fields);

        long status = draft.executorUsr() == null ? RequestStatus.NEW : RequestStatus.ASSIGNED;
        insertStatus(id, status, user.id(), now);
        if (draft.executorUsr() != null) {
            insertExecutor(id, draft.executorUsr(), user.id(), now);
        }
        Map<String, Object> info = new HashMap<>();
        info.put("repairRequest", id);
        info.put("dbegStatus", now);
        info.put("status", status);
        info.put("service", service);
        info.put("dbegExecutor", draft.executorUsr() == null ? null : now);
        info.put("executorUsr", draft.executorUsr());
        db.execSql(RequestSql.insertInfo(), info);
        savePhotos(id, photos, user.id(), now);
        return row(id);
    }

    /**
     * Исправить заявку (Ф-ЗАЯ-07): поля заявки меняются, прежние значения не хранятся; сменился тип неисправности -
     * служба определяется заново; новые фото добавляются.
     */
    @Transactional
    public RequestRow update(long id, RequestDraft draft, List<MultipartFile> photos) throws Exception {
        CurrentUser user = currentUserService.get();
        OffsetDateTime now = OffsetDateTime.now();
        RequestRow row = row(id);
        rights.checkEdit(user, row, now);
        Map<String, Object> fields = draftFields(draft);
        fields.put("id", id);
        db.updateRec("repair_request", fields);
        if (row.getFaultType() != draft.faultType()) {
            db.updateRec("repair_request_info", Map.of("service", faultService(draft.faultType())),
                    Map.of("repair_request", id));
        }
        savePhotos(id, photos, user.id(), now);
        return row(id);
    }

    /**
     * Выполнить действие над заявкой (Ф-ЗАЯ-03, Ф-ЗАЯ-07): запись в журнал и комментарий - пояснением к заявке.
     */
    @Transactional
    public RequestDetail action(long id, RequestAction action, String comment) throws Exception {
        CurrentUser user = currentUserService.get();
        RequestRow row = row(id);
        rights.checkAction(user, row, isVisible(user, id), action);
        String text = comment == null ? "" : comment.trim();
        if (action.commentRequired && text.isEmpty()) {
            throw ApiException.badRequest("Укажите комментарий");
        }
        Long executor = switch (action) {
            case TAKE, DETACH -> user.id();
            case RELEASE, FORWARD -> null;
            default -> row.getExecutorUsr() == null ? null : row.getExecutorUsr().id();
        };
        OffsetDateTime now = OffsetDateTime.now();
        move(row, action.status, executor, user.id(), now);
        if (!text.isEmpty()) {
            insertNote(id, text, user.id(), now);
        }
        return detail(id);
    }

    /**
     * Назначить исполнителя, сменить его или снять назначение (executor = null) - Ф-ЗАЯ-02. Новая заявка с
     * исполнителем становится «назначенной», назначенная без исполнителя - «новой»; в остальных статусах статус не
     * меняется.
     */
    @Transactional
    public RequestDetail assign(long id, Long executor) throws Exception {
        CurrentUser user = currentUserService.get();
        RequestRow row = row(id);
        rights.checkAssign(user, row);
        Long current = row.getExecutorUsr() == null ? null : row.getExecutorUsr().id();
        if (Objects.equals(current, executor)) {
            return detail(id);
        }
        long status = row.getRequestStatusType();
        if (executor != null) {
            checkStaff(row.getService(), executor);
            if (status == RequestStatus.NEW) {
                status = RequestStatus.ASSIGNED;
            }
        } else if (status == RequestStatus.ASSIGNED) {
            status = RequestStatus.NEW;
        } else {
            throw ApiException.badRequest("Снять исполнителя можно только с назначенной заявки");
        }
        move(row, status, executor, user.id(), OffsetDateTime.now());
        return detail(id);
    }

    /**
     * Пояснение к заявке: текст и фото (Ф-ЗАЯ-05).
     */
    @Transactional
    public RequestDetail addNote(long id, String text, List<MultipartFile> photos) throws Exception {
        CurrentUser user = currentUserService.get();
        row(id);
        String note = text == null ? "" : text.trim();
        if (note.isEmpty() && (photos == null || photos.isEmpty())) {
            throw ApiException.badRequest("Напишите пояснение или приложите фото");
        }
        // пояснение и фото - с одним временем: в истории они показываются вместе
        OffsetDateTime now = OffsetDateTime.now();
        if (!note.isEmpty()) {
            insertNote(id, note, user.id(), now);
        }
        savePhotos(id, photos, user.id(), now);
        return detail(id);
    }

    // --- внутреннее

    private List<RequestRow> load(RequestFilter filter, boolean byStatus, ListSort sort) throws Exception {
        CurrentUser user = currentUserService.get();
        SqlQuery q = RequestSql.list(filter, user.id(), visibility(user), List.copyOf(RequestRights.FINAL_STATUSES),
                byStatus, sort);
        return rows(db.loadSql(q.sql(), q.params()), RequestRow::new);
    }

    /**
     * Параметры условия видимости заявки для исполнения (RequestSql, VISIBLE) - по сотруднику.
     */
    private static Map<String, Object> visibility(CurrentUser user) {
        Map<String, Object> res = new HashMap<>();
        res.put("admin", user.hasRole(RoleType.ADMIN));
        res.put("services", user.roles().stream().map(CurrentUser.Role::service).filter(Objects::nonNull)
                .distinct().toList());
        res.put("myTrip", user.trip() == null ? null : user.trip().id());
        res.put("myDepotStation", user.depotStation());
        res.put("tripLocation", LocationType.TRIP);
        return res;
    }

    /**
     * Видна ли заявка сотруднику для исполнения (RequestSql, VISIBLE).
     */
    private boolean isVisible(CurrentUser user, long id) throws Exception {
        Map<String, Object> params = visibility(user);
        params.put("id", id);
        return db.loadSqlRec(RequestSql.visible(), params) != null;
    }

    /**
     * Строка заявки.
     *
     * @throws ApiException 404 - заявки нет
     */
    private RequestRow row(long id) throws Exception {
        return row(id, RequestRow::new);
    }

    /**
     * Строка заявки или карточка: объект создаёт create.
     *
     * @throws ApiException 404 - заявки нет
     */
    private <T extends RequestRow> T row(long id, Supplier<T> create) throws Exception {
        DbRec rec = db.loadSqlRec(RequestSql.one(), Map.of("id", id, "tripLocation", LocationType.TRIP));
        if (rec == null) {
            throw ApiException.notFound("Заявка не найдена");
        }
        return rows(List.of(rec), create).get(0);
    }

    /**
     * Строки заявок: номера вагонов, имена сотрудников и названия рейсов - одним запросом на все строки каждые.
     */
    private <T extends RequestRow> List<T> rows(List<DbRec> recs, Supplier<T> create) throws Exception {
        if (recs.isEmpty()) {
            return List.of();
        }
        Set<Long> wagonIds = new HashSet<>();
        Set<Long> usrIds = new HashSet<>();
        Set<Long> tripIds = new HashSet<>();
        for (DbRec rec : recs) {
            wagonIds.add(rec.getLong("wagon"));
            usrIds.add(rec.getLong("created_by_usr"));
            addNotNull(usrIds, rec.getLong("executor_usr", null));
            addNotNull(tripIds, rec.getLong("trip", null));
            addNotNull(tripIds, rec.getLong("wagon_trip", null));
        }
        Map<Long, String> wagons = new HashMap<>();
        for (DbRec w : db.loadList("wagon", Map.of("id", wagonIds))) {
            wagons.put(w.getLong("id"), w.getString("number"));
        }
        Map<Long, UserRef> users = users(usrIds);
        Map<Long, String> trips = tripNames(tripIds);

        List<T> res = new ArrayList<>(recs.size());
        for (DbRec rec : recs) {
            Long location = rec.getLong("location_type", null);
            boolean inTrip = location != null && location == LocationType.TRIP;
            Long wagonTrip = rec.getLong("wagon_trip", null);
            Long trip = rec.getLong("trip", null);
            Long executor = rec.getLong("executor_usr", null);
            T row = create.get();
            row.setId(rec.getLong("id"));
            row.setNumber(rec.getString("number"));
            row.setWagon(rec.getLong("wagon"));
            row.setWagonNumber(wagons.get(rec.getLong("wagon")));
            row.setWagonType(rec.getLong("wagon_type"));
            row.setLocationType(location);
            row.setPlace(inTrip ? null : rec.getLong("wagon_place", null));
            row.setWagonTrip(wagonTrip);
            row.setWagonTripName(wagonTrip == null ? null : trips.get(wagonTrip));
            row.setWagonStation(inTrip ? null : rec.getLong("wagon_station", null));
            row.setTrip(trip == null ? null : new TripRef(trip, trips.get(trip)));
            row.setService(rec.getLong("service"));
            row.setFaultType(rec.getLong("fault_type"));
            row.setSeverityType(rec.getLong("severity_type"));
            row.setCreatedByUsr(users.get(rec.getLong("created_by_usr")));
            row.setCreatedDt(rec.getDateTimeTZ("created_dt"));
            row.setDescription(rec.getString("description"));
            row.setRequestStatusType(rec.getLong("request_status_type"));
            row.setExecutorUsr(executor == null ? null : users.get(executor));
            row.setPhotoCount(rec.getInt("photo_count"));
            res.add(row);
        }
        return res;
    }

    /**
     * Сотрудники по id: фамилия и инициалы.
     */
    private Map<Long, UserRef> users(Set<Long> ids) throws Exception {
        Map<Long, UserRef> res = new HashMap<>();
        if (ids.isEmpty()) {
            return res;
        }
        for (DbRec u : db.loadList("usr", Map.of("id", ids))) {
            long id = u.getLong("id");
            res.put(id, new UserRef(id, CurrentUser.shortName(u.getString("full_name"))));
        }
        return res;
    }

    /**
     * Рейсы по id для показа: номер поезда и название маршрута рейса.
     */
    private Map<Long, String> tripNames(Set<Long> ids) throws Exception {
        Map<Long, String> res = new HashMap<>();
        if (ids.isEmpty()) {
            return res;
        }
        List<DbRec> trips = db.loadList("trip", Map.of("id", ids));
        Set<Long> routeIds = new HashSet<>();
        for (DbRec t : trips) {
            routeIds.add(t.getLong("route"));
        }
        Map<Long, DbRec> routes = new HashMap<>();
        for (DbRec r : db.loadList("route", Map.of("id", routeIds))) {
            routes.put(r.getLong("id"), r);
        }
        for (DbRec t : trips) {
            DbRec route = routes.get(t.getLong("route"));
            res.put(t.getLong("id"), route.getString("train_number") + " " + route.getString("name"));
        }
        return res;
    }

    /**
     * Поля заявки из черновика: критичность по умолчанию - из неисправности.
     *
     * @throws ApiException 400 - вагона, неисправности или рейса нет
     */
    private Map<String, Object> draftFields(RequestDraft draft) throws Exception {
        DbRec wagon = draft.wagon() == null ? null : db.loadRec("wagon", draft.wagon());
        if (wagon == null) {
            throw ApiException.badRequest("Выберите вагон");
        }
        DbRec fault = draft.faultType() == null ? null : db.loadRec("fault_type", draft.faultType());
        if (fault == null) {
            throw ApiException.badRequest("Выберите неисправность");
        }
        if (draft.trip() != null && db.loadRec("trip", draft.trip()) == null) {
            throw ApiException.badRequest("Рейс не найден");
        }
        Long severity = draft.severityType() != null ? draft.severityType() : fault.getLong("severity_type");

        Map<String, Object> res = new HashMap<>();
        res.put("wagon", wagon.getLong("id"));
        res.put("trip", draft.trip());
        res.put("fault_type", fault.getLong("id"));
        res.put("severity_type", severity);
        res.put("description", draft.description() == null ? "" : draft.description().trim());
        return res;
    }

    /**
     * Служба-исполнитель типа неисправности; не задана - служба категории неисправности.
     */
    private long faultService(long faultType) throws Exception {
        DbRec fault = db.loadRec("fault_type", faultType);
        Long service = fault.getLong("service", null);
        if (service != null) {
            return service;
        }
        return db.loadRec("fault_category_type", fault.getLong("fault_category_type"), true).getLong("service");
    }

    /**
     * Исполнитель - сотрудник службы заявки (Ф-ЗАЯ-02).
     *
     * @throws ApiException 400 - не сотрудник службы
     */
    private void checkStaff(long service, long executor) throws Exception {
        if (serviceStaff(service).stream().noneMatch(u -> u.id() == executor)) {
            throw ApiException.badRequest("Исполнитель - не сотрудник службы заявки");
        }
    }

    /**
     * Следующий номер заявки в году: код компании, год (две цифры), порядковый номер - «KZR-26-00042».
     */
    private String nextNumber(OffsetDateTime now) throws Exception {
        db.loadSqlRec(RequestSql.lockNumbers(), Map.of("key", NUMBER_LOCK));
        String prefix = String.format("%s-%02d-", COMPANY_CODE, now.getYear() % 100);
        DbRec max = db.loadSqlRec(RequestSql.maxNumber(), Map.of("prefix", prefix + "%"));
        String last = max == null ? null : max.getString("max_number");
        int next = last == null ? 1 : Integer.parseInt(last.substring(prefix.length())) + 1;
        return prefix + String.format("%05d", next);
    }

    /**
     * Записи журналов заявки по времени начала.
     */
    private Map<Instant, DbRec> journalByTime(String table, long id) throws Exception {
        Map<Instant, DbRec> res = new HashMap<>();
        for (DbRec rec : db.loadList(table, Map.of("repair_request", id))) {
            res.put(rec.getDateTimeTZ("dbeg").toInstant(), rec);
        }
        return res;
    }

    /**
     * Новые записи журналов заявки: запись журнала статусов - если статус сменился или исполнитель остался прежним
     * (действие без смены статуса и исполнителя - передача в депо новой заявки), запись журнала исполнителей - если
     * сменился исполнитель. Текущая запись журнала закрывается в момент now, новая действует с now; копия - в
     * repair_request_info.
     */
    private void move(RequestRow row, long status, Long executor, long by, OffsetDateTime now) throws Exception {
        long id = row.getId();
        Long prevExecutor = row.getExecutorUsr() == null ? null : row.getExecutorUsr().id();
        boolean executorChanged = !Objects.equals(prevExecutor, executor);
        Map<String, Object> info = new HashMap<>();
        if (status != row.getRequestStatusType() || !executorChanged) {
            closeCurrent("repair_request_status", id, now);
            insertStatus(id, status, by, now);
            info.put("dbeg_status", now);
            info.put("request_status_type", status);
        }
        if (executorChanged) {
            closeCurrent("repair_request_executor", id, now);
            insertExecutor(id, executor, by, now);
            info.put("dbeg_executor", now);
            info.put("executor_usr", executor);
        }
        db.updateRec("repair_request_info", info, Map.of("repair_request", id));
    }

    /**
     * Текущая запись журнала заявки; null - записей нет.
     */
    private DbRec currentRec(String table, long id) throws Exception {
        Map<String, Object> key = new HashMap<>();
        key.put("repair_request", id);
        key.put("dend", null);
        return db.loadRec(table, key);
    }

    private void closeCurrent(String table, long id, OffsetDateTime now) throws Exception {
        DbRec current = currentRec(table, id);
        if (current != null) {
            db.updateRec(table, Map.of("id", current.getLong("id"), "dend", now));
        }
    }

    private void insertStatus(long id, long status, long by, OffsetDateTime now) throws Exception {
        db.insertRec("repair_request_status", Map.of("repair_request", id, "dbeg", now, "request_status_type", status,
                "created_by_usr", by));
    }

    private void insertExecutor(long id, Long executor, long by, OffsetDateTime now) throws Exception {
        Map<String, Object> rec = new HashMap<>();
        rec.put("repair_request", id);
        rec.put("dbeg", now);
        rec.put("executor_usr", executor);
        rec.put("created_by_usr", by);
        db.insertRec("repair_request_executor", rec);
    }

    private void insertNote(long id, String text, long usr, OffsetDateTime now) throws Exception {
        db.insertRec("repair_request_note", Map.of("repair_request", id, "created_dt", now, "created_by_usr", usr,
                "note", text));
    }

    /**
     * Фото - в хранилище файлов, ключи - в repair_request_photo.
     *
     * @param now время фото: с временем подачи - фото заявки, с временем пояснения - фото к пояснению
     * @throws ApiException 400 - файл не изображение
     */
    private void savePhotos(long id, List<MultipartFile> photos, long usr, OffsetDateTime now) throws Exception {
        if (photos == null) {
            return;
        }
        for (MultipartFile file : photos) {
            String type = file.getContentType();
            if (type == null || !type.startsWith("image/")) {
                throw ApiException.badRequest("Приложить можно только фото");
            }
            String key = "requests/" + id + "/" + UUID.randomUUID();
            try (var in = file.getInputStream()) {
                storage.put(key, in, file.getSize(), type);
            }
            Map<String, Object> rec = new HashMap<>();
            rec.put("repair_request", id);
            rec.put("storage_key", key);
            rec.put("size_bytes", (int) file.getSize());
            rec.put("created_by_usr", usr);
            rec.put("created_dt", now);
            db.insertRec("repair_request_photo", rec);
        }
    }

    /**
     * Действие записи журналов для истории - по прежнему и новому статусу и исполнителю. Комментарий обязателен у
     * «передать в депо» и «ТОР» и не вводится у «снять себя», «взять в работу» и снятия назначения - по нему их и
     * различаем.
     *
     * @param hasNote у записи есть комментарий (пояснение того же сотрудника в тот же момент)
     * @return действие (RequestAction); null - назначение исполнителя
     */
    private static String historyAction(long prev, Long prevExecutor, long status, Long executor, boolean hasNote) {
        if (status == prev) {
            if (status == RequestStatus.NEW && Objects.equals(executor, prevExecutor)) {
                return RequestAction.FORWARD.name();
            }
            if (status == RequestStatus.IN_PROGRESS && hasNote) {
                return RequestAction.DETACH.name();
            }
            return null;
        }
        if (status == RequestStatus.IN_PROGRESS) {
            if (prev == RequestStatus.DONE) {
                return RequestAction.RETURN.name();
            }
            return hasNote ? RequestAction.DETACH.name() : RequestAction.TAKE.name();
        }
        if (status == RequestStatus.DONE) {
            return RequestAction.COMPLETE.name();
        }
        if (status == RequestStatus.CLOSED) {
            return RequestAction.ACCEPT.name();
        }
        if (status == RequestStatus.NEW) {
            if (hasNote) {
                return RequestAction.FORWARD.name();
            }
            return prev == RequestStatus.IN_PROGRESS ? RequestAction.RELEASE.name() : null;
        }
        if (status == RequestStatus.ASSIGNED) {
            return null;
        }
        if (status == RequestStatus.REJECTED) {
            return prev == RequestStatus.IN_PROGRESS ? RequestAction.NOT_DONE.name() : RequestAction.REJECT.name();
        }
        if (status == RequestStatus.DEFERRED) {
            return RequestAction.DEFER.name();
        }
        return RequestAction.CANCEL.name();
    }

    /**
     * Ключ группы «сотрудник и момент»: пояснение и фото, сохранённые вместе.
     */
    private static String groupKey(long usr, OffsetDateTime at) {
        return usr + "@" + at.toInstant();
    }

    private static void addNotNull(Set<Long> set, Long value) {
        if (value != null) {
            set.add(value);
        }
    }

}
