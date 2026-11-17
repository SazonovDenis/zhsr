package kz.zhsr.app.service.equip;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.EquipBasis;
import kz.zhsr.app.constants.EquipQuantityKind;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.equip.EquipForm;
import kz.zhsr.app.dto.equip.EquipFormItem;
import kz.zhsr.app.dto.equip.EquipItemRow;
import kz.zhsr.app.dto.equip.EquipListDetail;
import kz.zhsr.app.dto.equip.EquipListDraft;
import kz.zhsr.app.dto.equip.EquipListFilter;
import kz.zhsr.app.dto.equip.EquipListRow;
import kz.zhsr.app.dto.equip.EquipListValueRow;
import kz.zhsr.app.dto.equip.EquipNormRow;
import kz.zhsr.app.dto.equip.EquipTemplateRow;
import kz.zhsr.app.dto.equip.EquipValueDraft;
import kz.zhsr.app.service.ApiException;
import kz.zhsr.app.service.SqlQuery;
import kz.zhsr.app.service.check.CheckRights;
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
import java.util.Set;
import java.util.UUID;

/**
 * Описи экипировки: опись для заполнения, составление описи (Ф-ЭКП-02), список и карточка (Ф-ЭКП-03).
 * <p>
 * Опись приходит на сервер завершённой и фиксирует текущий факт: сколько каждой позиции лежит в вагоне; автор
 * исправляет опись в течение {@link #EDIT_PERIOD} после последнего сохранения.
 * Норматив подбирается, как у чек-листа: норматив типа вагона, иначе общий. Требуемое количество = количество на
 * единицу базы × база; число мест, купе и окон - у модели вагона. Добавка для головного и хвостового вагона не
 * учитывается (техдолг). Недостача по описи - одна заявка (Ф-ЗАЯ-10), если у шаблона задана неисправность.
 */
@Service
public class EquipService {

    /**
     * Имя части запроса с фото позиции: photo_ и id пункта шаблона.
     */
    public static final String PHOTO_PART = "photo_";

    /**
     * Срок исправления описи после последнего сохранения.
     */
    public static final Duration EDIT_PERIOD = Duration.ofMinutes(10);

    private final Db db;
    private final CurrentUserService currentUserService;
    private final RequestService requestService;
    private final RefResolver refResolver;
    private final FileStorage storage;

    public EquipService(Db db, CurrentUserService currentUserService, RequestService requestService,
                        FileStorage storage, RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.requestService = requestService;
        this.refResolver = refResolver;
        this.storage = storage;
    }

    // --- чтение

    /**
     * Включённые шаблоны описей - для выбора при новой описи.
     */
    public List<EquipTemplateRow> templates() throws Exception {
        currentUserService.get();
        return EquipAdminService.sortedTemplates(
                db.loadList("equip_list_template", Map.of("disabled", false), EquipTemplateRow.class));
    }

    /**
     * Опись для заполнения: позиции, требуемое количество для вагона и количество из прошлой описи.
     *
     * @param exclude исправляемая опись: прошлая опись - до неё; null - новая опись
     */
    public EquipForm form(long template, long wagon, Long exclude) throws Exception {
        CheckRights.checkEquip(currentUserService.get());
        EquipForm res = new EquipForm();
        res.setTemplate(template(template));
        res.setItems(formItems(template, wagonRec(wagon), exclude == null ? 0 : exclude));
        return res;
    }

    /**
     * Описи по фильтру (Ф-ЭКП-03): видимость - как у проверок ({@link CheckRights#seesAll}).
     */
    public List<EquipListRow> list(EquipListFilter filter, ListSort sort) throws Exception {
        CurrentUser user = currentUserService.get();
        SqlQuery q = EquipSql.list(filter, user.id(), CheckRights.seesAll(user), CheckRights.scope(user), sort);
        List<EquipListRow> res = db.loadSql(q.sql(), q.params(), EquipListRow.class);
        refResolver.resolve(res);
        return res;
    }

    /**
     * Карточка описи: значения и все пункты шаблона (и отключённые).
     *
     * @throws ApiException 404 - описи нет или она не видна сотруднику
     */
    public EquipListDetail detail(long id) throws Exception {
        CurrentUser user = currentUserService.get();
        EquipListDetail res = db.loadSqlRec(EquipSql.one(), Map.of("id", id), EquipListDetail.class);
        DbRec own = res == null ? null : db.loadRec("equip_list", id, true);
        SqlQuery seen = EquipSql.visibleOne(id, user.id(), CheckRights.scope(user));
        if (res == null || (!CheckRights.seesAll(user) && db.loadSqlRec(seen.sql(), seen.params()) == null)) {
            throw ApiException.notFound("Опись не найдена");
        }
        return fill(res, own);
    }

    /**
     * Последняя опись вагона - список имущества вагона в карточке вагона: видна всем, без ограничения видимости описей.
     *
     * @return null - описей у вагона нет
     */
    public EquipListDetail lastOfWagon(long wagon) throws Exception {
        currentUserService.get();
        DbRec last = db.loadSqlRec(EquipSql.lastOfWagon(), Map.of("wagon", wagon));
        if (last == null) {
            return null;
        }
        long id = last.getLong("id");
        EquipListDetail res = db.loadSqlRec(EquipSql.one(), Map.of("id", id), EquipListDetail.class);
        return fill(res, db.loadRec("equip_list", id, true));
    }

    /** Карточка описи res: пункты шаблона и значения с фото. own - запись equip_list. */
    private EquipListDetail fill(EquipListDetail res, DbRec own) throws Exception {
        long id = own.getLong("id");
        res.setItems(EquipAdminService.sortedItems(db.loadList("equip_list_template_item",
                Map.of("equip_list_template", own.getLong("equip_list_template")), EquipItemRow.class)));
        List<EquipListValueRow> values = db.loadList("equip_list_value", Map.of("equip_list", id),
                EquipListValueRow.class);
        if (!values.isEmpty()) {
            Map<Long, List<DbRec>> photos = UtDb.collectGroupBy_records(db.loadList("equip_list_value_photo",
                    Map.of("equip_list_value", values.stream().map(EquipListValueRow::getId).toList())),
                    "equip_list_value");
            for (EquipListValueRow v : values) {
                v.setPhotos(photos.getOrDefault(v.getId(), List.of()).stream().map(r -> r.getLong("id")).sorted()
                                  .toList());
            }
        }
        res.setValues(values);
        refResolver.resolve(res);
        return res;
    }

    /**
     * Файл фото к значению позиции.
     *
     * @throws ApiException 404 - фото нет
     */
    public FileStorage.StoredFile photo(long id) throws Exception {
        DbRec photo = db.loadRec("equip_list_value_photo", id);
        if (photo == null) {
            throw ApiException.notFound("Фото не найдено");
        }
        return storage.get(photo.getString("storage_key"));
    }

    // --- составление описи

    /**
     * Сохранить завершённую опись (Ф-ЭКП-02): посчитать требуемое количество и нарушения, сохранить примечания и фото,
     * создать одну заявку по недостаче.
     *
     * @param files части запроса; фото позиции - части {@link #PHOTO_PART}id пункта шаблона
     * @throws ApiException 400 - позиция не из описи, количество не указано или меньше нуля, файл не изображение;
     *                      403 - сотрудник не составляет опись
     */
    @Transactional
    public EquipListDetail create(EquipListDraft draft, MultiValueMap<String, MultipartFile> files) throws Exception {
        CurrentUser user = currentUserService.get();
        CheckRights.checkEquip(user);
        EquipTemplateRow tpl = template(draft.equipListTemplate());
        DbRec wagon = wagonRec(draft.wagon());
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime started = draft.createdDt() == null || draft.createdDt().isAfter(now) ? now : draft.createdDt();

        Long trip = wagonTrip(draft.wagon());
        Map<String, Object> list = new HashMap<>();
        list.put("equip_list_template", tpl.getId());
        list.put("wagon", draft.wagon());
        list.put("trip", trip);
        list.put("created_by_usr", user.id());
        list.put("created_dt", started);
        list.put("completed_dt", now);
        long equipList = db.insertRec("equip_list", list);

        Shortage shortage = saveValues(equipList, tpl, wagon, draft, files, user.id(), now);
        if (!shortage.lines().isEmpty() && tpl.getFaultType() != null) {
            long request = requestService.createSystem(draft.wagon(), trip, tpl.getFaultType(),
                    shortage.text(tpl), user.id(), now, shortage.photos());
            db.updateRec("equip_list", Map.of("id", equipList, "repair_request", request));
        }
        return detail(equipList);
    }

    /**
     * Исправить опись (Ф-ЭКП-02): автор и администратор в течение {@link #EDIT_PERIOD} после последнего сохранения.
     * Значения, примечания и фото заменяются; нарушения пересчитываются. Заявка по недостаче: есть и недостача есть -
     * описание и фото заявки обновляются; недостачи больше нет - заявка отменяется от имени автора описи; заявки не
     * было, а недостача появилась - заявка создаётся. Шаблон и вагон - из сохранённой описи.
     *
     * @throws ApiException 404 - описи нет; 403 - исправляет не автор или срок вышел; 400 - как при сохранении
     */
    @Transactional
    public EquipListDetail update(long id, EquipListDraft draft, MultiValueMap<String, MultipartFile> files)
            throws Exception {
        CurrentUser user = currentUserService.get();
        DbRec own = db.loadRec("equip_list", id);
        if (own == null) {
            throw ApiException.notFound("Опись не найдена");
        }
        OffsetDateTime now = OffsetDateTime.now();
        long author = own.getLong("created_by_usr");
        if (author != user.id() && !user.hasRole(RoleType.ADMIN)) {
            throw ApiException.forbidden("Исправляет опись только её автор");
        }
        if (own.getDateTimeTZ("completed_dt").plus(EDIT_PERIOD).isBefore(now)) {
            throw ApiException.forbidden("Исправить опись можно в течение 10 минут после сохранения");
        }
        EquipTemplateRow tpl = db.loadRec("equip_list_template", own.getLong("equip_list_template"),
                EquipTemplateRow.class);
        long wagonId = own.getLong("wagon");
        DbRec wagon = wagonRec(wagonId);
        db.updateRec("equip_list", Map.of("id", id, "completed_dt", now));

        Shortage shortage = saveValues(id, tpl, wagon, draft, files, user.id(), now);
        Long request = own.getLong("repair_request", null);
        if (request != null) {
            if (!shortage.lines().isEmpty()) {
                requestService.updateSystem(request, shortage.text(tpl), shortage.photos(), author);
            } else if (requestService.cancelSystem(request, "Недостачу по описи исправили", author)) {
                Map<String, Object> rec = new HashMap<>();
                rec.put("id", id);
                rec.put("repair_request", null);
                db.updateRec("equip_list", rec);
            }
        } else if (!shortage.lines().isEmpty() && tpl.getFaultType() != null) {
            long created = requestService.createSystem(wagonId, own.getLong("trip", null), tpl.getFaultType(),
                    shortage.text(tpl), author, now, shortage.photos());
            db.updateRec("equip_list", Map.of("id", id, "repair_request", created));
        }
        return detail(id);
    }

    /**
     * Недостача по описи: строки описания заявки и фото позиций с недостачей.
     */
    private record Shortage(List<String> lines, List<RequestService.StoredPhoto> photos) {

        String text(EquipTemplateRow tpl) {
            return "Опись «" + tpl.getTextRu() + "», недостача:\n- " + String.join("\n- ", lines);
        }
    }

    /**
     * Сохранить значения описи: новые - вставить, сохранённые (исправление) - заменить; примечания, фото.
     *
     * @return недостача - для заявки
     * @throws ApiException 400 - позиция не из описи или указана дважды, количество не указано или меньше нуля
     */
    private Shortage saveValues(long equipList, EquipTemplateRow tpl, DbRec wagon, EquipListDraft draft,
                                MultiValueMap<String, MultipartFile> files, long usr, OffsetDateTime now)
            throws Exception {
        Map<Long, EquipFormItem> items = new LinkedHashMap<>();
        for (EquipFormItem item : formItems(tpl.getId(), wagon, equipList)) {
            if (item.getUnit() != null) {
                items.put(item.getId(), item);
            }
        }
        Map<Long, EquipValueDraft> drafts = new HashMap<>();
        for (EquipValueDraft v : draft.values() == null ? List.<EquipValueDraft>of() : draft.values()) {
            if (!items.containsKey(v.equipListTemplateItem()) || drafts.put(v.equipListTemplateItem(), v) != null) {
                throw ApiException.badRequest("Позиция не из этой описи или указана дважды");
            }
        }
        Map<Long, List<MultipartFile>> photos = photos(files, items.keySet());
        Map<Long, EquipListValueRow> saved = new HashMap<>();
        for (EquipListValueRow v : db.loadList("equip_list_value", Map.of("equip_list", equipList),
                EquipListValueRow.class)) {
            saved.put(v.getEquipListTemplateItem(), v);
        }

        List<String> lines = new ArrayList<>();
        List<RequestService.StoredPhoto> shortPhotos = new ArrayList<>();
        for (EquipFormItem item : items.values()) {
            EquipValueDraft v = drafts.get(item.getId());
            BigDecimal quantity = v == null ? null : v.quantityActual();
            if (quantity == null || quantity.signum() < 0) {
                throw ApiException.badRequest("Укажите количество, не меньше нуля: " + item.getTextRu());
            }
            BigDecimal required = item.getQuantityRequired();
            boolean violation = required != null && (item.getQuantityKind() == EquipQuantityKind.EXACT
                    ? quantity.compareTo(required) != 0
                    : quantity.compareTo(required) < 0);
            Map<String, Object> rec = new HashMap<>();
            rec.put("quantity_actual", quantity);
            rec.put("quantity_required", required);
            rec.put("equip_list_item_norm", item.getEquipListItemNorm());
            rec.put("violation", violation);
            rec.put("comment", trim(v.comment()));
            EquipListValueRow old = saved.get(item.getId());
            long valueId;
            if (old == null) {
                rec.put("equip_list", equipList);
                rec.put("equip_list_template_item", item.getId());
                valueId = db.insertRec("equip_list_value", rec);
            } else {
                valueId = old.getId();
                rec.put("id", valueId);
                db.updateRec("equip_list_value", rec);
            }
            List<RequestService.StoredPhoto> stored = keepPhotos(valueId, v.photos());
            stored.addAll(savePhotos(equipList, valueId, photos.getOrDefault(item.getId(), List.of()), usr, now));
            if (required != null && quantity.compareTo(required) < 0) {
                String comment = trim(v.comment());
                lines.add(item.getTextRu() + ": требуется " + plain(required) + ", фактически " + plain(quantity)
                          + " " + item.getUnit() + (comment == null ? "" : ". " + comment));
                shortPhotos.addAll(stored);
            }
        }
        return new Shortage(lines, shortPhotos);
    }

    /**
     * Оставить у значения сохранённые фото keep, остальные удалить вместе с файлами.
     *
     * @return оставленные фото
     */
    private List<RequestService.StoredPhoto> keepPhotos(long value, List<Long> keep) throws Exception {
        Set<Long> ids = keep == null ? Set.of() : new HashSet<>(keep);
        List<RequestService.StoredPhoto> res = new ArrayList<>();
        for (DbRec photo : db.loadList("equip_list_value_photo", Map.of("equip_list_value", value))) {
            String key = photo.getString("storage_key");
            if (ids.contains(photo.getLong("id"))) {
                res.add(new RequestService.StoredPhoto(key, photo.getInt("size_bytes")));
            } else {
                db.deleteRec("equip_list_value_photo", photo.getLong("id"));
                db.execSql(EquipSql.deleteRequestPhotos(), Map.of("key", key));
                storage.remove(key);
            }
        }
        return res;
    }

    /**
     * Фото позиций из частей запроса.
     *
     * @throws ApiException 400 - часть с фото не той позиции или файл не изображение
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
                throw ApiException.badRequest("Фото к позиции не из этой описи");
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
     * Фото позиции - в хранилище файлов, ключи - в equip_list_value_photo.
     *
     * @return сохранённые фото: заявка по недостаче ссылается на те же файлы
     */
    private List<RequestService.StoredPhoto> savePhotos(long equipList, long value, List<MultipartFile> files,
                                                        long usr, OffsetDateTime now) throws Exception {
        List<RequestService.StoredPhoto> res = new ArrayList<>();
        for (MultipartFile file : files) {
            String key = "equiplists/" + equipList + "/" + UUID.randomUUID();
            try (var in = file.getInputStream()) {
                storage.put(key, in, file.getSize(), file.getContentType());
            }
            Map<String, Object> rec = new HashMap<>();
            rec.put("equip_list_value", value);
            rec.put("storage_key", key);
            rec.put("size_bytes", (int) file.getSize());
            rec.put("created_by_usr", usr);
            rec.put("created_dt", now);
            rec.put("received_dt", now);
            db.insertRec("equip_list_value_photo", rec);
            res.add(new RequestService.StoredPhoto(key, (int) file.getSize()));
        }
        return res;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // --- внутреннее

    /**
     * Включённый шаблон описи.
     *
     * @throws ApiException 400 - шаблона нет или он отключён
     */
    private EquipTemplateRow template(long id) throws Exception {
        EquipTemplateRow tpl = db.loadRec("equip_list_template", id, EquipTemplateRow.class);
        if (tpl == null || tpl.isDisabled()) {
            throw ApiException.badRequest("Шаблон описи не найден или отключён");
        }
        return tpl;
    }

    /**
     * @throws ApiException 400 - вагона нет
     */
    private DbRec wagonRec(long wagon) throws Exception {
        DbRec rec = db.loadRec("wagon", wagon);
        if (rec == null) {
            throw ApiException.badRequest("Выберите вагон");
        }
        return rec;
    }

    /**
     * Рейс, в составе которого вагон сейчас; null - вагон не в рейсе.
     */
    private Long wagonTrip(long wagon) throws Exception {
        DbRec info = db.loadRec("wagon_info", Map.of("wagon", wagon));
        return info != null && info.getLong("location_type") == LocationType.TRIP ? info.getLong("trip", null) : null;
    }

    /**
     * Показанные пункты шаблона в порядке показа (как у чек-листа): у позиции - норматив для типа вагона, требуемое
     * количество и количество из прошлой описи вагона по этому шаблону.
     */
    private List<EquipFormItem> formItems(long template, DbRec wagon, long exclude) throws Exception {
        List<EquipFormItem> all = EquipAdminService.sortedItems(db.loadList("equip_list_template_item",
                Map.of("equip_list_template", template, "disabled", false), EquipFormItem.class));
        if (all.isEmpty()) {
            return all;
        }
        Map<String, Object> key = new HashMap<>();
        key.put("equip_list_template_item", all.stream().map(EquipItemRow::getId).toList());
        key.put("dend", null);
        Map<Long, List<EquipNormRow>> norms = new HashMap<>();
        for (EquipNormRow n : db.loadList("equip_list_item_norm", key, EquipNormRow.class)) {
            norms.computeIfAbsent(n.getEquipListTemplateItem(), k -> new ArrayList<>()).add(n);
        }
        Long modelType = wagon.getLong("model_type", null);
        DbRec model = modelType == null ? null : db.loadRec("model_type", modelType);
        Map<Long, BigDecimal> previous = previous(wagon.getLong("id"), template, exclude);
        Long wagonType = wagon.getLong("wagon_type");

        List<EquipFormItem> shown = new ArrayList<>();
        for (EquipFormItem item : all) {
            List<EquipNormRow> itemNorms = norms.getOrDefault(item.getId(), List.of());
            EquipNormRow norm = pick(itemNorms, wagonType);
            if (item.getUnit() != null && !itemNorms.isEmpty() && norm == null) {
                continue;
            }
            if (norm != null) {
                item.setEquipListItemNorm(norm.getId());
                item.setNormText(norm.getNormText());
                item.setQuantityKind(norm.getQuantityKind());
                item.setCritical(norm.isCritical());
                BigDecimal base = base(model, norm.getBasis());
                item.setQuantityRequired(base == null ? null : norm.getQuantityPerBasis().multiply(base));
            }
            item.setQuantityPrevious(previous.get(item.getId()));
            shown.add(item);
        }
        return withoutEmptySections(shown);
    }

    /**
     * Число единиц базы расчёта у вагона: вагон - 1; место, купе, окно - из модели вагона; null - не задано.
     */
    private static BigDecimal base(DbRec model, int basis) {
        if (basis == EquipBasis.WAGON) {
            return BigDecimal.ONE;
        }
        String column = switch (basis) {
            case EquipBasis.SEAT -> "seats";
            case EquipBasis.COMPARTMENT -> "compartments";
            default -> "windows";
        };
        Object value = model == null ? null : model.get(column);
        return value == null ? null : BigDecimal.valueOf(((Number) value).longValue());
    }

    private static EquipNormRow pick(List<EquipNormRow> norms, Long wagonType) {
        EquipNormRow general = null;
        for (EquipNormRow n : norms) {
            if (n.getWagonType() == null) {
                general = n;
            } else if (n.getWagonType().equals(wagonType)) {
                return n;
            }
        }
        return general;
    }

    private static List<EquipFormItem> withoutEmptySections(List<EquipFormItem> items) {
        Set<Long> used = new HashSet<>();
        Map<Long, Long> parent = new HashMap<>();
        for (EquipFormItem i : items) {
            parent.put(i.getId(), i.getParentItem());
        }
        for (EquipFormItem i : items) {
            if (i.getUnit() != null) {
                for (Long p = i.getParentItem(); p != null && used.add(p); p = parent.get(p)) {
                    // разделы-предки позиции показываются
                }
            }
        }
        return items.stream().filter(i -> i.getUnit() != null || used.contains(i.getId())).toList();
    }

    /**
     * Пункт - фактическое количество в последней описи вагона по шаблону, кроме описи exclude; описей не было - пусто.
     */
    private Map<Long, BigDecimal> previous(long wagon, long template, long exclude) throws Exception {
        DbRec last = db.loadSqlRec(EquipSql.last(), Map.of("wagon", wagon, "template", template, "exclude", exclude));
        Map<Long, BigDecimal> res = new HashMap<>();
        if (last != null) {
            for (EquipListValueRow v : db.loadList("equip_list_value", Map.of("equip_list", last.getLong("id")),
                    EquipListValueRow.class)) {
                res.put(v.getEquipListTemplateItem(), v.getQuantityActual());
            }
        }
        return res;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

}
