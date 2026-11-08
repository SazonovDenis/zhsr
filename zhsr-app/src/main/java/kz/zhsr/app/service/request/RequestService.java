package kz.zhsr.app.service.request;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RequestStatus;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.request.RequestCounts;
import kz.zhsr.app.dto.request.RequestDetail;
import kz.zhsr.app.dto.request.RequestDraft;
import kz.zhsr.app.dto.request.RequestExecutorRecord;
import kz.zhsr.app.dto.request.RequestFilter;
import kz.zhsr.app.dto.request.RequestNote;
import kz.zhsr.app.dto.request.RequestPhoto;
import kz.zhsr.app.dto.request.RequestRow;
import kz.zhsr.app.dto.request.RequestStatusRecord;
import kz.zhsr.app.service.ApiException;
import kz.zhsr.app.service.OfflineReceiptService;
import kz.zhsr.app.service.SqlQuery;
import kz.zhsr.app.service.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Заявки на ремонт (Ф-ЗАЯ-01 … Ф-ЗАЯ-07): список, карточка с историей, подача, исправление, действия, назначение
 * исполнителя, примечания и фото. Права - только через {@link RequestRights}.
 * <p>
 * Статус заявки - журнал repair_request_status, исполнитель - журнал repair_request_executor (текущая запись - dend
 * пуст), их копия - repair_request_info; смена статуса, комментарий к ней и копия - в одной транзакции. Первая запись
 * журнала статусов вносится при подаче. Записи журналов одного действия - с одним временем. Служба заявки определяется
 * типом неисправности и запоминается в repair_request_info при подаче и при смене типа исправлением заявки. Фото - в
 * хранилище файлов ({@link FileStorage}), в БД - ключ файла; фото, сохранённое в один момент с примечанием того же
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
    private final RefResolver refResolver;
    private final OfflineReceiptService offlineReceipt;

    public RequestService(Db db, CurrentUserService currentUserService, RequestRights rights, FileStorage storage,
                          RefResolver refResolver, OfflineReceiptService offlineReceipt) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.rights = rights;
        this.storage = storage;
        this.refResolver = refResolver;
        this.offlineReceipt = offlineReceipt;
    }

    // --- чтение

    /**
     * Список заявок по фильтру (Ф-ЗАЯ-06). По умолчанию - сначала новые.
     */
    public List<RequestRow> list(RequestFilter filter, ListSort sort) throws Exception {
        List<RequestRow> res = load(filter, true, sort);
        refResolver.resolve(res);
        return res;
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
     * Карточка заявки: поля и плоские списки журналов, примечаний и фото.
     *
     * @throws ApiException 404 - заявки нет
     */
    public RequestDetail detail(long id) throws Exception {
        RequestDetail row = row(id, RequestDetail.class);

        Map<String, Object> params = Map.of("id", id);
        row.setStatuses(db.loadSql(RequestSql.statuses(), params, RequestStatusRecord.class));
        row.setExecutors(db.loadSql(RequestSql.executors(), params, RequestExecutorRecord.class));
        row.setNotes(db.loadSql(RequestSql.notes(), params, RequestNote.class));
        row.setPhotos(db.loadSql(RequestSql.photos(), params, RequestPhoto.class));
        refResolver.resolve(row.getStatuses());
        refResolver.resolve(row.getExecutors());
        refResolver.resolve(row.getNotes());
        refResolver.resolve(row.getPhotos());
        return row;
    }

    /**
     * Сотрудники службы - кому можно назначить заявку.
     */
    public List<UserRef> serviceStaff(long service) throws Exception {
        List<UserRef> res = db.loadSql(RequestSql.serviceStaff(), Map.of("service", service), UserRef.class);
        refResolver.resolve(res);
        return res;
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
     * «назначена». Время создания - когда пользователь создал заявку (она могла ждать связи на телефоне), им же
     * начинаются журналы и помечаются фото заявки; время поступления - время сервера.
     *
     * @return созданная заявка; повтор уже принятой отправки - она же: из ответа телефон узнаёт id заявки
     */
    @Transactional
    public RequestRow create(RequestDraft draft, List<MultipartFile> photos) throws Exception {
        if (!offlineReceipt.accept(draft.clientGuid())) {
            return row(offlineReceipt.requestOf(draft.clientGuid()));
        }
        CurrentUser user = currentUserService.get();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime created = clientTime(draft.clientDt(), now);
        Map<String, Object> fields = draftFields(draft);
        long service = faultService(draft.faultType());
        if (draft.executorUsr() != null) {
            rights.checkAssignOnCreate(user);
            checkStaff(service, draft.executorUsr());
        }

        fields.put("number", nextNumber(now));
        fields.put("created_by_usr", user.id());
        fields.put("created_dt", created);
        fields.put("received_dt", now);
        long id = db.insertRec("repair_request", fields);
        offlineReceipt.link(draft.clientGuid(), id);

        long status = draft.executorUsr() == null ? RequestStatus.NEW : RequestStatus.ASSIGNED;
        insertStatus(id, status, user.id(), created);
        if (draft.executorUsr() != null) {
            insertExecutor(id, draft.executorUsr(), user.id(), created);
        }
        Map<String, Object> info = new HashMap<>();
        info.put("repairRequest", id);
        info.put("dbegStatus", created);
        info.put("status", status);
        info.put("service", service);
        info.put("dbegExecutor", draft.executorUsr() == null ? null : created);
        info.put("executorUsr", draft.executorUsr());
        db.execSql(RequestSql.insertInfo(), info);
        savePhotos(id, photos, user.id(), created, now);
        return row(id);
    }

    /**
     * Исправить заявку (Ф-ЗАЯ-07): поля заявки меняются, прежние значения не хранятся; сменился тип неисправности -
     * служба определяется заново; новые фото добавляются. Срок исправления проверяется по времени, когда пользователь
     * исправил заявку: исправление могло ждать связи на телефоне. Повтор уже принятого исправления ничего не меняет.
     */
    @Transactional
    public RequestRow update(long id, RequestDraft draft, List<MultipartFile> photos) throws Exception {
        if (!offlineReceipt.accept(draft.clientGuid())) {
            return row(id);
        }
        CurrentUser user = currentUserService.get();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime edited = clientTime(draft.clientDt(), now);
        RequestRow row = row(id);
        rights.checkEdit(user, row, edited);
        Map<String, Object> fields = draftFields(draft);
        fields.put("id", id);
        db.updateRec("repair_request", fields);
        if (row.getFaultType() != draft.faultType()) {
            db.updateRec("repair_request_info", Map.of("service", faultService(draft.faultType())),
                    Map.of("repair_request", id));
        }
        savePhotos(id, photos, user.id(), edited, now);
        return row(id);
    }

    /**
     * Выполнить действие над заявкой (Ф-ЗАЯ-03, Ф-ЗАЯ-07): запись в журнал и комментарий - примечанием к заявке.
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
            default -> row.getExecutorUsr() == null ? null : row.getExecutorUsr().getId();
        };
        OffsetDateTime now = OffsetDateTime.now();
        move(row, action.status, executor, user.id(), now);
        if (!text.isEmpty()) {
            insertNote(id, text, user.id(), now, now);
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
        Long current = row.getExecutorUsr() == null ? null : row.getExecutorUsr().getId();
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
     * Примечание к заявке: текст и фото (Ф-ЗАЯ-05).
     *
     * @param clientDt   когда пользователь написал примечание, по часам телефона: оно могло ждать связи на телефоне;
     *                   null - время сервера
     * @param clientGuid guid примечания, присвоен телефоном: повтор уже принятого примечания ничего не меняет
     */
    @Transactional
    public RequestDetail addNote(long id, String text, OffsetDateTime clientDt, String clientGuid,
                                 List<MultipartFile> photos) throws Exception {
        if (!offlineReceipt.accept(clientGuid)) {
            return detail(id);
        }
        CurrentUser user = currentUserService.get();
        row(id);
        String note = text == null ? "" : text.trim();
        if (note.isEmpty() && (photos == null || photos.isEmpty())) {
            throw ApiException.badRequest("Напишите примечание или приложите фото");
        }
        // примечание и фото - с одним временем: в истории они показываются вместе
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime at = clientTime(clientDt, now);
        if (!note.isEmpty()) {
            insertNote(id, note, user.id(), at, now);
        }
        savePhotos(id, photos, user.id(), at, now);
        return detail(id);
    }

    /**
     * Одно фото к заявке с телефона: каждое фото - своя отправка, дошедшие фото не отправляются повторно.
     *
     * @param clientDt   время заявки, исправления или примечания, с которыми приложено фото; null - время сервера
     * @param clientGuid guid фото, присвоен телефоном: повтор уже принятого фото ничего не меняет
     */
    @Transactional
    public void addPhoto(long id, MultipartFile photo, OffsetDateTime clientDt, String clientGuid) throws Exception {
        if (!offlineReceipt.accept(clientGuid)) {
            return;
        }
        CurrentUser user = currentUserService.get();
        row(id);
        OffsetDateTime now = OffsetDateTime.now();
        savePhotos(id, List.of(photo), user.id(), clientTime(clientDt, now), now);
    }

    // --- внутреннее

    /**
     * Время действия пользователя. Не передано или позже времени сервера (часы телефона спешат) - время сервера.
     */
    private static OffsetDateTime clientTime(OffsetDateTime clientDt, OffsetDateTime now) {
        return clientDt == null || clientDt.isAfter(now) ? now : clientDt;
    }

    private List<RequestRow> load(RequestFilter filter, boolean byStatus, ListSort sort) throws Exception {
        CurrentUser user = currentUserService.get();
        SqlQuery q = RequestSql.list(filter, user.id(), visibility(user), List.copyOf(RequestRights.FINAL_STATUSES),
                byStatus, sort);
        return db.loadSql(q.sql(), q.params(), RequestRow.class);
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
        return row(id, RequestRow.class);
    }

    /**
     * Строка заявки или карточка.
     *
     * @throws ApiException 404 - заявки нет
     */
    private <T extends RequestRow> T row(long id, Class<T> clazz) throws Exception {
        T row = db.loadSqlRec(RequestSql.one(), Map.of("id", id, "tripLocation", LocationType.TRIP), clazz);
        if (row == null) {
            throw ApiException.notFound("Заявка не найдена");
        }
        refResolver.resolve(row);
        return row;
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
     * Служба-исполнитель типа неисправности.
     */
    private long faultService(long faultType) throws Exception {
        return db.loadRec("fault_type", faultType, true).getLong("service");
    }

    /**
     * Исполнитель - сотрудник службы заявки (Ф-ЗАЯ-02).
     *
     * @throws ApiException 400 - не сотрудник службы
     */
    private void checkStaff(long service, long executor) throws Exception {
        if (serviceStaff(service).stream().noneMatch(u -> u.getId() == executor)) {
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
     * Новые записи журналов заявки: запись журнала статусов - если статус сменился или исполнитель остался прежним
     * (действие без смены статуса и исполнителя - передача в депо новой заявки), запись журнала исполнителей - если
     * сменился исполнитель. Текущая запись журнала закрывается в момент now, новая действует с now; копия - в
     * repair_request_info.
     */
    private void move(RequestRow row, long status, Long executor, long by, OffsetDateTime now) throws Exception {
        long id = row.getId();
        Long prevExecutor = row.getExecutorUsr() == null ? null : row.getExecutorUsr().getId();
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

    private void insertNote(long id, String text, long usr, OffsetDateTime at, OffsetDateTime received)
            throws Exception {
        db.insertRec("repair_request_note", Map.of("repair_request", id, "created_dt", at, "received_dt", received,
                "created_by_usr", usr, "note", text));
    }

    /**
     * Фото - в хранилище файлов, ключи - в repair_request_photo.
     *
     * @param at       когда фото приложено: время создания заявки - фото заявки, другое время - запись истории
     * @param received время поступления на сервер
     * @throws ApiException 400 - файл не изображение
     */
    private void savePhotos(long id, List<MultipartFile> photos, long usr, OffsetDateTime at, OffsetDateTime received)
            throws Exception {
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
            rec.put("created_dt", at);
            rec.put("received_dt", received);
            db.insertRec("repair_request_photo", rec);
        }
    }

}
