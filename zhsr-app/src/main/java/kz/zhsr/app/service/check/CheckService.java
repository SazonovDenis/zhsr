package kz.zhsr.app.service.check;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.CheckObject;
import kz.zhsr.app.constants.CheckValueKind;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.dto.check.CheckDictValueRow;
import kz.zhsr.app.dto.check.CheckForm;
import kz.zhsr.app.dto.check.CheckFormItem;
import kz.zhsr.app.dto.check.CheckItemRow;
import kz.zhsr.app.dto.check.CheckListDetail;
import kz.zhsr.app.dto.check.CheckListDraft;
import kz.zhsr.app.dto.check.CheckListFilter;
import kz.zhsr.app.dto.check.CheckListRow;
import kz.zhsr.app.dto.check.CheckListValueRow;
import kz.zhsr.app.dto.check.CheckNormRow;
import kz.zhsr.app.dto.check.CheckTemplateRow;
import kz.zhsr.app.dto.check.CheckValueDraft;
import kz.zhsr.app.service.ApiException;
import kz.zhsr.app.service.SqlQuery;
import kz.zhsr.app.service.request.RequestService;
import kz.zhsr.app.service.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
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
 * Проверки по чек-листу: чек-лист для заполнения, проведение проверки (Ф-ЧЕК-03), список и карточка (Ф-ЧЕК-04).
 * <p>
 * Чек-лист приходит на сервер завершённым; автор исправляет чек-лист в течение {@link #EDIT_PERIOD} после последнего
 * сохранения. Для пункта Система подбирает текущий (незакрытый) норматив: для
 * вагона - норматив типа вагона, иначе общий (тип вагона пуст); для сотрудника и рейса - общий. Пункт показывается,
 * если у пункта нет нормативов или для объекта проверки нашёлся норматив; раздел - если в нём есть показанные пункты.
 * У значения сохраняются норматив и признак нарушения. Заявки по нарушениям (Ф-ЗАЯ-10) создаются только при проверке
 * вагона; фото пункта становятся фото заявки ссылкой на тот же файл.
 */
@Service
public class CheckService {

    /**
     * Имя части запроса с фото пункта: photo_ и id пункта шаблона.
     */
    public static final String PHOTO_PART = "photo_";

    /**
     * Срок исправления чек-листа после последнего сохранения.
     */
    public static final Duration EDIT_PERIOD = Duration.ofMinutes(10);

    private final Db db;
    private final CurrentUserService currentUserService;
    private final CheckAdminService checkAdminService;
    private final RequestService requestService;
    private final FileStorage storage;
    private final RefResolver refResolver;

    public CheckService(Db db, CurrentUserService currentUserService, CheckAdminService checkAdminService,
                        RequestService requestService, FileStorage storage, RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.checkAdminService = checkAdminService;
        this.requestService = requestService;
        this.storage = storage;
        this.refResolver = refResolver;
    }

    // --- чтение

    /**
     * Включённые шаблоны чек-листов - для выбора при новой проверке; какие из них доступны сотруднику, решает фронт по
     * объекту проверки.
     */
    public List<CheckTemplateRow> templates() throws Exception {
        currentUserService.get();
        return CheckAdminService.sortedTemplates(
                db.loadList("check_list_template", Map.of("disabled", false), CheckTemplateRow.class));
    }

    /**
     * Чек-лист для заполнения: пункты и нормативы для объекта проверки.
     *
     * @param wagon вагон - при проверке вагона
     */
    public CheckForm form(long template, Long wagon) throws Exception {
        CurrentUser user = currentUserService.get();
        CheckTemplateRow tpl = template(template);
        CheckRights.checkCheck(user, tpl.getCheckObject());
        CheckForm res = new CheckForm();
        res.setTemplate(tpl);
        res.setItems(formItems(template, wagonType(tpl, wagon)));
        res.setDictValues(dictValues(res.getItems(), false));
        return res;
    }

    /**
     * Проверки по фильтру (Ф-ЧЕК-04): сотрудник видит проверки, которые провёл сам, и по своим вагонам и рейсу
     * ({@link CheckRights#scope}); роли из {@link CheckRights#SEE_ALL} и администратор - все.
     */
    public List<CheckListRow> list(CheckListFilter filter) throws Exception {
        CurrentUser user = currentUserService.get();
        SqlQuery q = CheckSql.list(filter, user.id(), CheckRights.seesAll(user), CheckRights.scope(user));
        List<CheckListRow> res = db.loadSql(q.sql(), q.params(), CheckListRow.class);
        refResolver.resolve(res);
        return res;
    }

    /**
     * Запрос нашёл строку.
     */
    private boolean visible(SqlQuery q) throws Exception {
        return db.loadSqlRec(q.sql(), q.params()) != null;
    }

    /**
     * Карточка проверки: значения, фото, все пункты шаблона (и отключённые) и варианты ответа.
     *
     * @throws ApiException 404 - проверки нет или она не видна сотруднику
     */
    public CheckListDetail detail(long id) throws Exception {
        CurrentUser user = currentUserService.get();
        CheckListDetail res = db.loadSqlRec(CheckSql.one(), Map.of("id", id), CheckListDetail.class);
        if (res == null) {
            throw ApiException.notFound("Проверка не найдена");
        }
        DbRec own = db.loadRec("check_list", id, true);
        if (!CheckRights.seesAll(user) && !visible(CheckSql.visibleOne(id, user.id(), CheckRights.scope(user)))) {
            throw ApiException.notFound("Проверка не найдена");
        }
        res.setItems(CheckAdminService.sortedItems(db.loadList("check_list_template_item",
                Map.of("check_list_template", own.getLong("check_list_template")), CheckItemRow.class)));
        List<CheckListValueRow> values = db.loadList("check_list_value", Map.of("check_list", id),
                CheckListValueRow.class);
        if (!values.isEmpty()) {
            Map<Long, List<DbRec>> photos = UtDb.collectGroupBy_records(db.loadList("check_list_value_photo",
                    Map.of("check_list_value", values.stream().map(CheckListValueRow::getId).toList())),
                    "check_list_value");
            for (CheckListValueRow v : values) {
                v.setPhotos(photos.getOrDefault(v.getId(), List.of()).stream().map(r -> r.getLong("id")).sorted()
                                  .toList());
            }
        }
        res.setValues(values);
        res.setDictValues(dictValues(res.getItems(), true));
        refResolver.resolve(res);
        refResolver.resolve(values);
        return res;
    }

    /**
     * Файл фото к значению пункта.
     *
     * @throws ApiException 404 - фото нет
     */
    public FileStorage.StoredFile photo(long id) throws Exception {
        DbRec photo = db.loadRec("check_list_value_photo", id);
        if (photo == null) {
            throw ApiException.notFound("Фото не найдено");
        }
        return storage.get(photo.getString("storage_key"));
    }

    // --- проведение проверки

    /**
     * Сохранить завершённый чек-лист (Ф-ЧЕК-03): проверить значения по нормативам, сохранить фото, создать заявки по
     * нарушениям.
     *
     * @param files части запроса; фото пункта - части {@link #PHOTO_PART}id пункта шаблона
     * @throws ApiException 400 - объект проверки не выбран, пункт не из чек-листа, значение не того вида, обязательный
     *                      пункт пуст, файл не изображение; 403 - сотрудник не проводит такую проверку
     */
    @Transactional
    public CheckListDetail create(CheckListDraft draft, MultiValueMap<String, MultipartFile> files) throws Exception {
        CurrentUser user = currentUserService.get();
        CheckTemplateRow tpl = template(draft.checkListTemplate());
        CheckRights.checkCheck(user, tpl.getCheckObject());
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime started = draft.createdDt() == null || draft.createdDt().isAfter(now) ? now : draft.createdDt();

        Map<String, Object> list = new HashMap<>();
        list.put("check_list_template", tpl.getId());
        list.put("created_by_usr", user.id());
        list.put("created_dt", started);
        list.put("completed_dt", now);
        Long wagonType = subject(tpl, draft, list);

        Map<Long, CheckFormItem> items = new LinkedHashMap<>();
        for (CheckFormItem item : formItems(tpl.getId(), wagonType)) {
            if (item.getValueKind() != null) {
                items.put(item.getId(), item);
            }
        }
        Map<Long, CheckValueDraft> values = new HashMap<>();
        for (CheckValueDraft v : draft.values() == null ? List.<CheckValueDraft>of() : draft.values()) {
            if (!items.containsKey(v.checkListTemplateItem()) || values.put(v.checkListTemplateItem(), v) != null) {
                throw ApiException.badRequest("Пункт не из этого чек-листа или указан дважды");
            }
        }
        Map<Long, List<MultipartFile>> photos = photos(files, items.keySet());
        Map<Long, Set<Long>> dictOf = dictMembership(items.values());

        long checkList = db.insertRec("check_list", list);
        for (CheckFormItem item : items.values()) {
            CheckValueDraft v = values.get(item.getId());
            Map<String, Object> rec = valueColumns(item, v, dictOf);
            rec.put("check_list", checkList);
            long valueId = db.insertRec("check_list_value", rec);
            List<RequestService.StoredPhoto> stored = savePhotos(checkList, valueId,
                    photos.getOrDefault(item.getId(), List.of()), user.id(), started, now);
            if (Boolean.TRUE.equals(rec.get("violation")) && item.getFaultType() != null
                && tpl.getCheckObject() == CheckObject.WAGON) {
                long request = requestService.createSystem((Long) list.get("wagon"), (Long) list.get("trip"),
                        item.getFaultType(), requestText(tpl, item, v), user.id(), now, stored);
                db.updateRec("check_list_value", Map.of("id", valueId, "repair_request", request));
            }
        }
        return detail(checkList);
    }

    /**
     * Исправить чек-лист (Ф-ЧЕК-03): автор и администратор в течение {@link #EDIT_PERIOD} после последнего сохранения.
     * Значения, описания и фото заменяются; нарушения пересчитываются. Заявка по пункту: есть и нарушение осталось -
     * описание и фото заявки обновляются; нарушения больше нет - заявка отменяется от имени автора чек-листа; заявки не
     * было, а нарушение появилось - заявка создаётся (только при проверке вагона). Шаблон и объект проверки - из
     * сохранённого чек-листа.
     *
     * @throws ApiException 404 - чек-листа нет; 403 - исправляет не автор или срок вышел; 400 - как при сохранении
     */
    @Transactional
    public CheckListDetail update(long id, CheckListDraft draft, MultiValueMap<String, MultipartFile> files)
            throws Exception {
        CurrentUser user = currentUserService.get();
        DbRec own = db.loadRec("check_list", id);
        if (own == null) {
            throw ApiException.notFound("Проверка не найдена");
        }
        OffsetDateTime now = OffsetDateTime.now();
        long author = own.getLong("created_by_usr");
        if (author != user.id() && !user.hasRole(RoleType.ADMIN)) {
            throw ApiException.forbidden("Исправляет чек-лист только его автор");
        }
        if (own.getDateTimeTZ("completed_dt").plus(EDIT_PERIOD).isBefore(now)) {
            throw ApiException.forbidden("Исправить чек-лист можно в течение 10 минут после сохранения");
        }
        CheckTemplateRow tpl = db.loadRec("check_list_template", own.getLong("check_list_template"),
                CheckTemplateRow.class);
        Long wagon = own.getLong("wagon", null);
        Long wagonType = wagon == null ? null : db.loadRec("wagon", wagon, true).getLong("wagon_type");
        db.updateRec("check_list", Map.of("id", id, "completed_dt", now));

        Map<Long, CheckFormItem> items = new LinkedHashMap<>();
        for (CheckFormItem item : formItems(tpl.getId(), wagonType)) {
            if (item.getValueKind() != null) {
                items.put(item.getId(), item);
            }
        }
        Map<Long, CheckValueDraft> values = new HashMap<>();
        for (CheckValueDraft v : draft.values() == null ? List.<CheckValueDraft>of() : draft.values()) {
            if (!items.containsKey(v.checkListTemplateItem()) || values.put(v.checkListTemplateItem(), v) != null) {
                throw ApiException.badRequest("Пункт не из этого чек-листа или указан дважды");
            }
        }
        Map<Long, List<MultipartFile>> photos = photos(files, items.keySet());
        Map<Long, Set<Long>> dictOf = dictMembership(items.values());
        Map<Long, DbRec> saved = new HashMap<>();
        for (DbRec v : db.loadList("check_list_value", Map.of("check_list", id))) {
            saved.put(v.getLong("check_list_template_item"), v);
        }

        for (CheckFormItem item : items.values()) {
            CheckValueDraft v = values.get(item.getId());
            Map<String, Object> rec = valueColumns(item, v, dictOf);
            DbRec old = saved.get(item.getId());
            long valueId;
            if (old == null) {
                rec.put("check_list", id);
                valueId = db.insertRec("check_list_value", rec);
            } else {
                valueId = old.getLong("id");
                rec.put("id", valueId);
                db.updateRec("check_list_value", rec);
            }
            List<RequestService.StoredPhoto> stored = keepPhotos(valueId, v == null ? null : v.photos());
            stored.addAll(savePhotos(id, valueId, photos.getOrDefault(item.getId(), List.of()), user.id(), now, now));
            boolean violation = Boolean.TRUE.equals(rec.get("violation"));
            Long request = old == null ? null : old.getLong("repair_request", null);
            if (request != null) {
                if (violation) {
                    requestService.updateSystem(request, requestText(tpl, item, v), stored, author);
                } else if (requestService.cancelSystem(request, "Нарушение по чек-листу исправили", author)) {
                    Map<String, Object> clear = new HashMap<>();
                    clear.put("id", valueId);
                    clear.put("repair_request", null);
                    db.updateRec("check_list_value", clear);
                }
            } else if (violation && item.getFaultType() != null && tpl.getCheckObject() == CheckObject.WAGON) {
                long created = requestService.createSystem(wagon, own.getLong("trip", null), item.getFaultType(),
                        requestText(tpl, item, v), author, now, stored);
                db.updateRec("check_list_value", Map.of("id", valueId, "repair_request", created));
            }
        }
        return detail(id);
    }

    // --- внутреннее

    /**
     * Оставить у значения сохранённые фото keep, остальные удалить вместе с файлами и фото заявок на этот файл.
     *
     * @return оставленные фото
     */
    private List<RequestService.StoredPhoto> keepPhotos(long value, List<Long> keep) throws Exception {
        Set<Long> ids = keep == null ? Set.of() : new HashSet<>(keep);
        List<RequestService.StoredPhoto> res = new ArrayList<>();
        for (DbRec photo : db.loadList("check_list_value_photo", Map.of("check_list_value", value))) {
            String key = photo.getString("storage_key");
            if (ids.contains(photo.getLong("id"))) {
                res.add(new RequestService.StoredPhoto(key, photo.getInt("size_bytes")));
            } else {
                db.deleteRec("check_list_value_photo", photo.getLong("id"));
                db.execSql(CheckSql.deleteRequestPhotos(), Map.of("key", key));
                storage.remove(key);
            }
        }
        return res;
    }

    /**
     * Включённый шаблон чек-листа.
     *
     * @throws ApiException 400 - шаблона нет или он отключён
     */
    private CheckTemplateRow template(long id) throws Exception {
        CheckTemplateRow tpl = db.loadRec("check_list_template", id, CheckTemplateRow.class);
        if (tpl == null || tpl.isDisabled()) {
            throw ApiException.badRequest("Шаблон чек-листа не найден или отключён");
        }
        return tpl;
    }

    /**
     * Тип вагона для подбора нормативов: у проверки вагона - тип выбранного вагона, иначе null.
     *
     * @throws ApiException 400 - у проверки вагона не выбран вагон
     */
    private Long wagonType(CheckTemplateRow tpl, Long wagon) throws Exception {
        if (tpl.getCheckObject() != CheckObject.WAGON) {
            return null;
        }
        DbRec rec = wagon == null ? null : db.loadRec("wagon", wagon);
        if (rec == null) {
            throw ApiException.badRequest("Выберите вагон");
        }
        return rec.getLong("wagon_type");
    }

    /**
     * Объект проверки в колонках чек-листа: вагон и его текущий рейс, рейс и член его бригады или рейс.
     *
     * @return тип вагона для подбора нормативов; null - проверяют не вагон
     * @throws ApiException 400 - объект проверки не выбран или сотрудник не из бригады рейса
     */
    private Long subject(CheckTemplateRow tpl, CheckListDraft draft, Map<String, Object> list) throws Exception {
        switch (tpl.getCheckObject()) {
            case CheckObject.WAGON -> {
                Long type = wagonType(tpl, draft.wagon());
                list.put("wagon", draft.wagon());
                list.put("trip", wagonTrip(draft.wagon()));
                return type;
            }
            case CheckObject.USR -> {
                requireTrip(draft.trip());
                if (draft.checkedUsr() == null
                    || db.loadList("trip_crew", Map.of("trip", draft.trip(), "usr", draft.checkedUsr())).isEmpty()) {
                    throw ApiException.badRequest("Выберите сотрудника из бригады рейса");
                }
                list.put("trip", draft.trip());
                list.put("checked_usr", draft.checkedUsr());
                return null;
            }
            default -> {
                requireTrip(draft.trip());
                list.put("trip", draft.trip());
                return null;
            }
        }
    }

    private void requireTrip(Long trip) throws Exception {
        if (trip == null || db.loadRec("trip", trip) == null) {
            throw ApiException.badRequest("Выберите рейс");
        }
    }

    /**
     * Рейс, в составе которого вагон сейчас; null - вагон не в рейсе.
     */
    private Long wagonTrip(long wagon) throws Exception {
        DbRec info = db.loadRec("wagon_info", Map.of("wagon", wagon));
        return info != null && info.getLong("location_type") == LocationType.TRIP ? info.getLong("trip", null) : null;
    }

    /**
     * Показанные пункты шаблона в порядке показа: включённые пункты, у которых нет нормативов или нашёлся норматив для
     * типа вагона; разделы, в которых есть показанные пункты. У пункта - подсказки подобранного норматива.
     *
     * @param wagonType тип вагона; null - только общие нормативы
     */
    private List<CheckFormItem> formItems(long template, Long wagonType) throws Exception {
        List<CheckFormItem> all = CheckAdminService.sortedItems(db.loadList("check_list_template_item",
                Map.of("check_list_template", template, "disabled", false), CheckFormItem.class));
        if (all.isEmpty()) {
            return all;
        }
        Map<String, Object> key = new HashMap<>();
        key.put("check_list_template_item", all.stream().map(CheckItemRow::getId).toList());
        key.put("dend", null);
        Map<Long, List<CheckNormRow>> norms = new HashMap<>();
        for (CheckNormRow n : checkAdminService.withValues(db.loadList("check_list_item_norm", key,
                CheckNormRow.class))) {
            norms.computeIfAbsent(n.getCheckListTemplateItem(), k -> new ArrayList<>()).add(n);
        }
        List<CheckFormItem> shown = new ArrayList<>();
        for (CheckFormItem item : all) {
            List<CheckNormRow> itemNorms = norms.getOrDefault(item.getId(), List.of());
            CheckNormRow norm = pick(itemNorms, wagonType);
            if (item.getValueKind() != null && !itemNorms.isEmpty() && norm == null) {
                continue;
            }
            if (norm != null) {
                item.setCheckListItemNorm(norm.getId());
                item.setRequired(norm.isRequired());
                item.setNumMin(norm.getNumMin());
                item.setNumMax(norm.getNumMax());
                item.setCritical(norm.isCritical());
                item.setCheckDictValues(norm.getCheckDictValues());
                item.setFaultType(norm.getFaultType());
            }
            shown.add(item);
        }
        return withoutEmptySections(shown);
    }

    /**
     * Норматив для типа вагона: норматив этого типа вагона, иначе общий; null - не нашёлся.
     */
    private static CheckNormRow pick(List<CheckNormRow> norms, Long wagonType) {
        CheckNormRow general = null;
        for (CheckNormRow n : norms) {
            if (n.getWagonType() == null) {
                general = n;
            } else if (n.getWagonType().equals(wagonType)) {
                return n;
            }
        }
        return general;
    }

    /**
     * Пункты без разделов, в которых нет показанных пунктов.
     */
    private static List<CheckFormItem> withoutEmptySections(List<CheckFormItem> items) {
        Set<Long> used = new HashSet<>();
        Map<Long, Long> parent = new HashMap<>();
        for (CheckFormItem i : items) {
            parent.put(i.getId(), i.getParentItem());
        }
        for (CheckFormItem i : items) {
            if (i.getValueKind() != null) {
                for (Long p = i.getParentItem(); p != null && used.add(p); p = parent.get(p)) {
                    // разделы-предки пункта со значением показываются
                }
            }
        }
        return items.stream().filter(i -> i.getValueKind() != null || used.contains(i.getId())).toList();
    }

    /**
     * Варианты ответа шаблонов ответов, выбранных у пунктов.
     *
     * @param withDisabled true - и отключённые (карточка проведённой проверки)
     */
    private List<CheckDictValueRow> dictValues(List<? extends CheckItemRow> items, boolean withDisabled)
            throws Exception {
        List<Long> dicts = items.stream().map(CheckItemRow::getCheckDict).filter(Objects::nonNull).distinct().toList();
        if (dicts.isEmpty()) {
            return List.of();
        }
        Map<String, Object> key = new HashMap<>();
        key.put("check_dict", dicts);
        if (!withDisabled) {
            key.put("disabled", false);
        }
        return CheckAdminService.sortedValues(db.loadList("check_dict_value", key, CheckDictValueRow.class));
    }

    /**
     * Шаблон ответов пункта - варианты ответа, которые можно выбрать (включённые).
     */
    private Map<Long, Set<Long>> dictMembership(Iterable<CheckFormItem> items) throws Exception {
        List<CheckFormItem> list = new ArrayList<>();
        items.forEach(list::add);
        Map<Long, Set<Long>> res = new HashMap<>();
        for (CheckDictValueRow v : dictValues(list, false)) {
            res.computeIfAbsent(v.getCheckDict(), k -> new HashSet<>()).add(v.getId());
        }
        return res;
    }

    /**
     * Колонки значения пункта: значение по виду значения пункта, описание нарушения, норматив и признак нарушения.
     *
     * @param v значение из чек-листа; null - пункт не заполнен
     * @throws ApiException 400 - значение не того вида, вариант ответа не из шаблона ответов, обязательный пункт пуст
     */
    private static Map<String, Object> valueColumns(CheckFormItem item, CheckValueDraft v,
                                                    Map<Long, Set<Long>> dictOf) {
        String string = v == null ? null : trim(v.valueString());
        BigDecimal number = v == null ? null : v.valueNumber();
        Long dictValue = v == null ? null : v.checkDictValue();
        int kind = item.getValueKind();
        if ((kind != CheckValueKind.STRING && string != null) || (kind != CheckValueKind.NUMBER && number != null)
            || (kind != CheckValueKind.DICT_VALUE && dictValue != null)) {
            throw ApiException.badRequest("Значение не того вида: " + item.getTextRu());
        }
        if (dictValue != null && !dictOf.getOrDefault(item.getCheckDict(), Set.of()).contains(dictValue)) {
            throw ApiException.badRequest("Вариант ответа не из шаблона ответов пункта: " + item.getTextRu());
        }
        if (item.isRequired() && string == null && number == null && dictValue == null) {
            throw ApiException.badRequest("Заполните пункт: " + item.getTextRu());
        }
        boolean violation = item.getCheckListItemNorm() != null && switch (kind) {
            case CheckValueKind.NUMBER -> number != null
                                          && ((item.getNumMin() != null && number.compareTo(item.getNumMin()) < 0)
                                              || (item.getNumMax() != null && number.compareTo(item.getNumMax()) > 0));
            case CheckValueKind.DICT_VALUE -> dictValue != null && !item.getCheckDictValues().isEmpty()
                                              && !item.getCheckDictValues().contains(dictValue);
            default -> false;
        };
        Map<String, Object> rec = new HashMap<>();
        rec.put("check_list_template_item", item.getId());
        rec.put("value_string", string);
        rec.put("value_number", number);
        rec.put("check_dict_value", dictValue);
        rec.put("comment", v == null ? null : trim(v.comment()));
        rec.put("check_list_item_norm", item.getCheckListItemNorm());
        rec.put("violation", violation);
        return rec;
    }

    /**
     * Фото пунктов из частей запроса.
     *
     * @throws ApiException 400 - часть с фото не того пункта или файл не изображение
     */
    private static Map<Long, List<MultipartFile>> photos(MultiValueMap<String, MultipartFile> files, Set<Long> items) {
        Map<Long, List<MultipartFile>> res = new HashMap<>();
        if (files == null) {
            return res;
        }
        for (Map.Entry<String, List<MultipartFile>> e : files.entrySet()) {
            if (!e.getKey().startsWith(PHOTO_PART)) {
                continue;
            }
            long item;
            try {
                item = Long.parseLong(e.getKey().substring(PHOTO_PART.length()));
            } catch (NumberFormatException ex) {
                throw ApiException.badRequest("Неверная часть запроса с фото");
            }
            if (!items.contains(item)) {
                throw ApiException.badRequest("Фото к пункту не из этого чек-листа");
            }
            for (MultipartFile f : e.getValue()) {
                if (f.getContentType() == null || !f.getContentType().startsWith("image/")) {
                    throw ApiException.badRequest("Приложить можно только фото");
                }
            }
            res.put(item, e.getValue());
        }
        return res;
    }

    /**
     * Фото пункта - в хранилище файлов, ключи - в check_list_value_photo.
     *
     * @return сохранённые фото: заявка по нарушению ссылается на те же файлы
     */
    private List<RequestService.StoredPhoto> savePhotos(long checkList, long value, List<MultipartFile> files,
                                                        long usr, OffsetDateTime at, OffsetDateTime received)
            throws Exception {
        List<RequestService.StoredPhoto> res = new ArrayList<>();
        for (MultipartFile file : files) {
            String key = "checklists/" + checkList + "/" + UUID.randomUUID();
            try (var in = file.getInputStream()) {
                storage.put(key, in, file.getSize(), file.getContentType());
            }
            Map<String, Object> rec = new HashMap<>();
            rec.put("check_list_value", value);
            rec.put("storage_key", key);
            rec.put("size_bytes", (int) file.getSize());
            rec.put("created_by_usr", usr);
            rec.put("created_dt", at);
            rec.put("received_dt", received);
            db.insertRec("check_list_value_photo", rec);
            res.add(new RequestService.StoredPhoto(key, (int) file.getSize()));
        }
        return res;
    }

    /**
     * Описание заявки по нарушению: шаблон, текст пункта, значение и описание нарушения.
     */
    private static String requestText(CheckTemplateRow tpl, CheckFormItem item, CheckValueDraft v) {
        StringBuilder res = new StringBuilder("Чек-лист «").append(tpl.getTextRu()).append("»: ")
                .append(item.getTextRu());
        if (v != null && v.valueNumber() != null) {
            res.append(" - ").append(v.valueNumber().stripTrailingZeros().toPlainString());
        }
        String comment = v == null ? null : trim(v.comment());
        if (comment != null) {
            res.append(". ").append(comment);
        }
        return res.toString();
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

}
