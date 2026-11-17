package kz.zhsr.app.service.request;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.CrewDuty;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RequestStatus;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.ref.CheckTemplateRef;
import kz.zhsr.app.dto.ref.EquipTemplateRef;
import kz.zhsr.app.dto.request.RequestActionBody;
import kz.zhsr.app.dto.request.RequestCounts;
import kz.zhsr.app.dto.request.RequestDetail;
import kz.zhsr.app.dto.request.RequestDraft;
import kz.zhsr.app.dto.request.RequestExecutorBody;
import kz.zhsr.app.dto.request.RequestExecutorRecord;
import kz.zhsr.app.dto.request.RequestFilter;
import kz.zhsr.app.dto.request.RequestNote;
import kz.zhsr.app.dto.request.RequestPhoto;
import kz.zhsr.app.dto.request.RequestRow;
import kz.zhsr.app.dto.request.RequestStatusRecord;
import kz.zhsr.app.dto.request.StaffCandidate;
import kz.zhsr.app.service.ApiException;
import kz.zhsr.app.service.OfflineReceiptService;
import kz.zhsr.app.service.SqlQuery;
import kz.zhsr.app.service.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /**
     * Начало ключа файла фото, приложенного к заявке напрямую; у фото чек-листа ключ другой.
     */
    private static final String PHOTO_PREFIX = "requests/";

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
     * Сколько заявок ждёт действия лично сотрудника (счётчик меню «Заявки»): столько же, сколько строк в списке «мне
     * на исполнение» без других отборов.
     */
    public int todoCount() throws Exception {
        RequestFilter filter = new RequestFilter("todo", null, null, null, null, null, null, null, null, null);
        return load(filter, true, new ListSort(null, false)).size();
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
        DbRec check = db.loadSqlRec(RequestSql.checkSource(), params);
        if (check != null) {
            row.setCheckList(check.getLong("id"));
            row.setCheckListTemplate(new CheckTemplateRef(check.getLong("check_list_template")));
        }
        DbRec equip = db.loadSqlRec(RequestSql.equipSource(), params);
        if (equip != null) {
            row.setEquipList(equip.getLong("id"));
            row.setEquipListTemplate(new EquipTemplateRef(equip.getLong("equip_list_template")));
        }
        refResolver.resolve(row);
        refResolver.resolve(row.getStatuses());
        refResolver.resolve(row.getExecutors());
        refResolver.resolve(row.getNotes());
        refResolver.resolve(row.getPhotos());
        return row;
    }

    /**
     * Кому можно назначить заявку (Ф-ЗАЯ-02).
     *
     * @param trip рейс: вся бригада этого рейса с должностью и вагонами, предпочтительно - сотрудники службы заявки
     *             (inService), так назначает ЛНП; null - все сотрудники службы
     */
    public List<StaffCandidate> serviceStaff(long service, Long trip) throws Exception {
        if (trip != null) {
            // строка бригады - дежурство при вагоне: проводник двух вагонов - две строки, второй вагон - wagon2
            Map<Long, StaffCandidate> byUsr = new LinkedHashMap<>();
            for (StaffCandidate c : db.loadSql(RequestSql.crewStaff(), Map.of("service", service, "trip", trip),
                    StaffCandidate.class)) {
                StaffCandidate first = byUsr.get(c.getUsr().getId());
                if (first == null) {
                    byUsr.put(c.getUsr().getId(), c);
                } else if (c.getWagon() != null) {
                    if (first.getWagon() == null) {
                        first.setWagon(c.getWagon());
                    } else if (first.getWagon2() == null) {
                        first.setWagon2(c.getWagon());
                    }
                }
            }
            List<StaffCandidate> res = new ArrayList<>(byUsr.values());
            refResolver.resolve(res);
            return res;
        }
        List<UserRef> users = db.loadSql(RequestSql.serviceStaff(), Map.of("service", service), UserRef.class);
        refResolver.resolve(users);
        return users.stream().map(u -> {
            StaffCandidate c = new StaffCandidate();
            c.setUsr(u);
            c.setInService(true);
            return c;
        }).toList();
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
            Long wagonTrip = wagonTrip(draft.wagon());
            rights.checkAssignOnCreate(user, wagonTrip);
            checkStaff(service, draft.executorUsr(), staffTrip(user, wagonTrip));
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
     * Фото, которое уже лежит в хранилище файлов: заявка получает ссылку на файл, файл не копируется.
     *
     * @param storageKey ключ файла в бакете
     * @param sizeBytes  размер, байт
     */
    public record StoredPhoto(String storageKey, int sizeBytes) {
    }

    /**
     * Создать заявку от имени Системы (Ф-ЗАЯ-10): по нарушению чек-листа или недостаче в описи экипировки. Заявка -
     * «новая», критичность - по умолчанию из неисправности, номер - как при подаче. Вызывается в транзакции чек-листа
     * или описи.
     *
     * @param author  автор чек-листа или описи
     * @param created время завершения чек-листа или описи
     * @param photos  фото, уже лежащие в хранилище файлов
     * @return id заявки
     */
    public long createSystem(long wagon, Long trip, long faultType, String description, long author,
                             OffsetDateTime created, List<StoredPhoto> photos) throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        DbRec fault = db.loadRec("fault_type", faultType, true);
        Map<String, Object> fields = new HashMap<>();
        fields.put("wagon", wagon);
        fields.put("trip", trip);
        fields.put("fault_type", faultType);
        fields.put("severity_type", fault.getLong("severity_type"));
        fields.put("description", description);
        fields.put("number", nextNumber(now));
        fields.put("created_by_usr", author);
        fields.put("created_dt", created);
        fields.put("received_dt", now);
        long id = db.insertRec("repair_request", fields);
        insertStatus(id, RequestStatus.NEW, author, created);
        Map<String, Object> info = new HashMap<>();
        info.put("repairRequest", id);
        info.put("dbegStatus", created);
        info.put("status", RequestStatus.NEW);
        info.put("service", fault.getLong("service"));
        info.put("dbegExecutor", null);
        info.put("executorUsr", null);
        db.execSql(RequestSql.insertInfo(), info);
        for (StoredPhoto photo : photos) {
            insertPhoto(id, photo.storageKey(), photo.sizeBytes(), author, created, now);
        }
        return id;
    }

    /**
     * Обновить системную заявку (Ф-ЗАЯ-10) при исправлении документа, по которому заявка создана (описи): описание
     * заменяется, фото заявки приводятся к photos - лишние записи фото удаляются (файлы - у документа), новые
     * добавляются временем создания заявки.
     */
    public void updateSystem(long id, String description, List<StoredPhoto> photos, long author) throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        DbRec request = db.loadRec("repair_request", id, true);
        db.updateRec("repair_request", Map.of("id", id, "description", description));
        Set<String> keep = new HashSet<>();
        for (StoredPhoto photo : photos) {
            keep.add(photo.storageKey());
        }
        Set<String> has = new HashSet<>();
        for (DbRec photo : db.loadList("repair_request_photo", Map.of("repair_request", id))) {
            String key = photo.getString("storage_key");
            if (keep.contains(key)) {
                has.add(key);
            } else if (!key.startsWith(PHOTO_PREFIX)) {
                db.deleteRec("repair_request_photo", photo.getLong("id"));
            }
        }
        for (StoredPhoto photo : photos) {
            if (!has.contains(photo.storageKey())) {
                insertPhoto(id, photo.storageKey(), photo.sizeBytes(), author,
                        request.getDateTimeTZ("created_dt"), now);
            }
        }
    }

    /**
     * Отменить системную заявку от имени автора: основания для заявки больше нет (недостачу по описи исправили).
     * Заявка не в статусе, из которого отменяют (уже в работе, выполнена, закрыта), не меняется.
     *
     * @return заявка отменена
     */
    public boolean cancelSystem(long id, String comment, long author) throws Exception {
        RequestRow row = row(id);
        if (!RequestAction.CANCEL.from.contains(row.getRequestStatusType())) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        Long executor = row.getExecutorUsr() == null ? null : row.getExecutorUsr().getId();
        move(row, RequestAction.CANCEL.status, executor, author, now);
        insertNote(id, comment, author, now, now);
        return true;
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
    public RequestDetail action(long id, RequestAction action, RequestActionBody body) throws Exception {
        if (body == null) {
            body = new RequestActionBody(null, null, null);
        }
        if (!offlineReceipt.accept(body.clientGuid())) {
            return detail(id);
        }
        CurrentUser user = currentUserService.get();
        RequestRow row = row(id);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime at = clientTime(body.clientDt(), now);
        requireUnchangedSince(id, body.clientDt());
        rights.checkAction(user, row, isVisible(user, id), action);
        String text = body.comment() == null ? "" : body.comment().trim();
        if (action.commentRequired && text.isEmpty()) {
            throw ApiException.badRequest("Укажите комментарий");
        }
        Long executor = switch (action) {
            case TAKE -> user.id();
            case RELEASE -> null;
            default -> row.getExecutorUsr() == null ? null : row.getExecutorUsr().getId();
        };
        move(row, action.status, executor, user.id(), at);
        if (!text.isEmpty()) {
            insertNote(id, text, user.id(), at, now);
        }
        return detail(id);
    }

    /**
     * Назначить исполнителя, сменить его или снять назначение (executor = null) - Ф-ЗАЯ-02. Новая заявка с
     * исполнителем становится «назначенной». Назначать, сменять и снимать исполнителя можно только у невыполненной
     * заявки ({@link RequestRights#checkAssign}). Без исполнителя назначенная и взятая в работу становятся «новыми»,
     * отложенная до ТО остаётся отложенной. В остальных случаях статус не меняется.
     */
    @Transactional
    public RequestDetail assign(long id, RequestExecutorBody body) throws Exception {
        if (!offlineReceipt.accept(body.clientGuid())) {
            return detail(id);
        }
        Long executor = body.executor();
        CurrentUser user = currentUserService.get();
        RequestRow row = row(id);
        requireUnchangedSince(id, body.clientDt());
        rights.checkAssign(user, row);
        Long current = row.getExecutorUsr() == null ? null : row.getExecutorUsr().getId();
        if (Objects.equals(current, executor)) {
            return detail(id);
        }
        long status = row.getRequestStatusType();
        if (executor != null) {
            checkStaff(row.getService(), executor,
                    staffTrip(user, row.getWagonTrip() == null ? null : row.getWagonTrip().getId()));
            if (status == RequestStatus.NEW) {
                status = RequestStatus.ASSIGNED;
            }
        } else if (status == RequestStatus.ASSIGNED || status == RequestStatus.IN_PROGRESS) {
            status = RequestStatus.NEW;
        } else if (status != RequestStatus.DEFERRED) {
            throw ApiException.badRequest("Снять исполнителя можно только с невыполненной заявки");
        }
        move(row, status, executor, user.id(), clientTime(body.clientDt(), OffsetDateTime.now()));
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

    /**
     * Исправить текст примечания (Ф-ЗАЯ-05): автор в течение 10 минут после создания. Прежний текст не хранится.
     *
     * @throws ApiException 404 - примечания нет; 400 - текст пуст
     */
    @Transactional
    public RequestDetail updateNote(long noteId, String text) throws Exception {
        CurrentUser user = currentUserService.get();
        DbRec note = db.loadRec("repair_request_note", noteId);
        if (note == null) {
            throw ApiException.notFound("Примечание не найдено");
        }
        rights.checkOwnChange(user, note.getLong("created_by_usr"), note.getDateTimeTZ("created_dt"),
                OffsetDateTime.now(), "Исправить примечание");
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            throw ApiException.badRequest("Напишите примечание");
        }
        db.updateRec("repair_request_note", Map.of("id", noteId, "note", value));
        return detail(note.getLong("repair_request"));
    }

    /**
     * Удалить фото заявки или примечания: автор в течение 10 минут после создания. Файл удаляется из хранилища, если
     * он приложен к заявке напрямую; файл фото чек-листа остаётся: на него ссылается чек-лист.
     *
     * @throws ApiException 404 - фото нет
     */
    @Transactional
    public RequestDetail deletePhoto(long photoId) throws Exception {
        CurrentUser user = currentUserService.get();
        DbRec photo = db.loadRec("repair_request_photo", photoId);
        if (photo == null) {
            throw ApiException.notFound("Фото не найдено");
        }
        rights.checkOwnChange(user, photo.getLong("created_by_usr"), photo.getDateTimeTZ("created_dt"),
                OffsetDateTime.now(), "Удалить фото");
        db.deleteRec("repair_request_photo", photoId);
        String key = photo.getString("storage_key");
        if (key.startsWith(PHOTO_PREFIX)) {
            storage.remove(key);
        }
        return detail(photo.getLong("repair_request"));
    }

    // --- внутреннее

    /**
     * Действие, которое ждало связи на телефоне, несвоевременно, если после него у заявки сменились статус или
     * исполнитель (журналы статусов и исполнителей): пользователь решал по устаревшему виду заявки.
     *
     * @param clientDt когда пользователь выполнил действие; null - действие не ждало, проверять нечего
     * @throws ApiException 409 - после действия заявка изменилась
     */
    private void requireUnchangedSince(long id, OffsetDateTime clientDt) throws Exception {
        if (clientDt != null && db.loadSqlRec(RequestSql.changedAfter(), Map.of("id", id, "at", clientDt)) != null) {
            throw ApiException.conflict("Пока действие ждало связи, статус или исполнитель заявки изменились");
        }
    }

    /**
     * Время действия пользователя. Не передано или позже времени сервера (часы телефона спешат) - время сервера.
     */
    private static OffsetDateTime clientTime(OffsetDateTime clientDt, OffsetDateTime now) {
        return clientDt == null || clientDt.isAfter(now) ? now : clientDt;
    }

    private List<RequestRow> load(RequestFilter filter, boolean byStatus, ListSort sort) throws Exception {
        CurrentUser user = currentUserService.get();
        // поиск по номеру, вагону и неисправности отменяет список и фильтры, кроме периода: ищется среди всех заявок
        // (Ф-ЗАЯ-06)
        if (filter.query() != null && !filter.query().isBlank()) {
            filter = new RequestFilter(null, null, filter.query(), null, null, null, null, null, filter.from(),
                    filter.to());
        }
        SqlQuery q = RequestSql.list(filter, user.id(), visibility(user), List.copyOf(RequestRights.FINAL_STATUSES),
                byStatus, sort);
        return db.loadSql(q.sql(), q.params(), RequestRow.class);
    }

    /**
     * Параметры условий видимости заявки для исполнения и списка «на исполнение» (RequestSql, VISIBLE и TODO) - по
     * сотруднику.
     */
    private static Map<String, Object> visibility(CurrentUser user) {
        Map<String, Object> res = new HashMap<>();
        res.put("admin", user.hasRole(RoleType.ADMIN));
        res.put("services", user.roles().stream().map(CurrentUser.Role::service).filter(Objects::nonNull)
                .distinct().toList());
        res.put("myTrip", user.trip() == null ? null : user.trip().id());
        res.put("myDepotStation", user.depotStation());
        res.put("tripLocation", LocationType.TRIP);
        // приём работы (RequestSql, ACCEPTABLE) и статусы списка «на исполнение» (RequestSql, TODO)
        CurrentUser.Trip trip = user.trip();
        res.put("myLnp", trip != null && trip.duty() == CrewDuty.LNP);
        res.put("myWagons", trip != null && trip.duty() == CrewDuty.CONDUCTOR ? trip.wagons() : List.of());
        res.put("acceptor", user.hasRole(RoleType.INSTRUCTOR) || user.hasRole(RoleType.OPERATOR));
        res.put("assigned", RequestStatus.ASSIGNED);
        res.put("inProgress", RequestStatus.IN_PROGRESS);
        res.put("done", RequestStatus.DONE);
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
     * Рейс, в составе которого вагон сейчас; null - вагон не в рейсе.
     */
    private Long wagonTrip(long wagon) throws Exception {
        DbRec info = db.loadRec("wagon_info", Map.of("wagon", wagon));
        return info != null && info.getLong("location_type") == LocationType.TRIP ? info.getLong("trip", null) : null;
    }

    /**
     * Служба-исполнитель типа неисправности.
     */
    private long faultService(long faultType) throws Exception {
        return db.loadRec("fault_type", faultType, true).getLong("service");
    }

    /**
     * Из кого выбирает исполнителя сотрудник: мастер депо и администратор - из всех сотрудников службы, ЛНП - из всей
     * бригады рейса вагона (Ф-ЗАЯ-02).
     *
     * @return рейс, бригадой которого ограничен выбор; null - не ограничен
     */
    private static Long staffTrip(CurrentUser user, Long wagonTrip) {
        return user.hasRole(RoleType.MASTER) || user.hasRole(RoleType.ADMIN) ? null : wagonTrip;
    }

    /**
     * Исполнитель - сотрудник службы заявки (Ф-ЗАЯ-02); ЛНП назначает любого сотрудника из бригады рейса.
     *
     * @param trip рейс, бригадой которого ограничен выбор; null - не ограничен
     * @throws ApiException 400 - не сотрудник службы или не из бригады рейса
     */
    private void checkStaff(long service, long executor, Long trip) throws Exception {
        if (serviceStaff(service, trip).stream().noneMatch(c -> c.getUsr().getId() == executor)) {
            throw ApiException.badRequest(trip == null
                    ? "Исполнитель - не сотрудник службы заявки"
                    : "Исполнитель - не из бригады рейса");
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
            String key = PHOTO_PREFIX + id + "/" + UUID.randomUUID();
            try (var in = file.getInputStream()) {
                storage.put(key, in, file.getSize(), type);
            }
            insertPhoto(id, key, (int) file.getSize(), usr, at, received);
        }
    }

    private void insertPhoto(long id, String key, int size, long usr, OffsetDateTime at, OffsetDateTime received)
            throws Exception {
        Map<String, Object> rec = new HashMap<>();
        rec.put("repair_request", id);
        rec.put("storage_key", key);
        rec.put("size_bytes", size);
        rec.put("created_by_usr", usr);
        rec.put("created_dt", at);
        rec.put("received_dt", received);
        db.insertRec("repair_request_photo", rec);
    }

}
