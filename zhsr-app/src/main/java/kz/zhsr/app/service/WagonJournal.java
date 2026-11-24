package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.WagonResponsibleKind;
import kz.zhsr.app.constants.WagonResponsiblePlanStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Журналы вагона: перевод в новое расположение и состояние, смена ответственных (факт журнала ответственных).
 */
@Service
public class WagonJournal {

    /**
     * Записи факта журнала ответственных, которые изменение открыло и закрыло.
     *
     * @param opened wagon_responsible.id открытой записи; null - изменение сняло всех ответственных
     * @param closed wagon_responsible.id закрытой записи; null - ответственных не было
     */
    public record Responsibles(Long opened, Long closed) {
    }

    /**
     * Записи журналов, открытые переводом.
     *
     * @param location     wagon_location.id
     * @param status       wagon_status.id
     * @param responsibles открытая и закрытая записи ответственных; null - перевод ответственных не менял
     */
    public record Opened(long location, long status, Responsibles responsibles) {
    }

    private final Db db;
    private final CurrentUserService currentUserService;

    public WagonJournal(Db db, CurrentUserService currentUserService) {
        this.db = db;
        this.currentUserService = currentUserService;
    }

    /**
     * Перевести вагон: закрыть текущие записи журналов временем time, открыть новые, обновить кэш wagon_info.
     * Вызывается внутри транзакции.
     *
     * @param station     станция, если расположение - станция
     * @param trip        рейс, если расположение - рейс
     * @param usrs        новые ответственные (usr); пусто - ответственный не назначен
     */
    public Opened move(long wagon, OffsetDateTime time, long locationType, Long station, Long trip, long status,
                       List<Long> usrs) throws Exception {
        Opened opened = moveKeepResponsible(wagon, time, locationType, station, trip, status);
        return new Opened(opened.location(), opened.status(), assign(wagon, time, usrs));
    }

    /**
     * Перевести вагон, как {@link #move}, но ответственного не менять: прежний остаётся. Вызывается внутри транзакции.
     *
     * @return открытые записи; responsibles - null
     */
    public Opened moveKeepResponsible(long wagon, OffsetDateTime time, long locationType, Long station, Long trip,
                                      long status) throws Exception {
        DbRec wagonStatus = db.loadRec("wagon_status", current(wagon));
        if (wagonStatus == null) {
            throw ApiException.conflict("У вагона нет текущего состояния");
        }
        if (!time.isAfter(wagonStatus.getDateTimeTZ("dbeg"))) {
            throw ApiException.badRequest("Время должно быть позже последнего изменения вагонов");
        }
        long locationId = openLocation(wagon, time, locationType, station, null, null, trip);
        db.updateRec("wagon_status", Map.of("id", wagonStatus.getLong("id"), "dend", time));
        long statusId = db.insertRec("wagon_status",
                Map.of("wagon", wagon, "dbeg", time, "wagon_status_type", status));

        Map<String, Object> info = locationInfo(time, locationType, station, null, null, trip);
        info.put("dbeg_status", time);
        info.put("wagon_status_type", status);
        db.updateRec("wagon_info", info, Map.of("wagon", wagon));
        return new Opened(locationId, statusId, null);
    }

    /**
     * Сменить состояние вагона; расположение не меняется. Вызывается внутри транзакции.
     *
     * @return wagon_status.id открытой записи
     */
    public long moveStatus(long wagon, OffsetDateTime time, long status) throws Exception {
        DbRec wagonStatus = db.loadRec("wagon_status", current(wagon));
        if (wagonStatus == null) {
            throw ApiException.conflict("У вагона нет текущего состояния");
        }
        if (!time.isAfter(wagonStatus.getDateTimeTZ("dbeg"))) {
            throw ApiException.badRequest("Время должно быть позже последнего изменения вагонов");
        }
        db.updateRec("wagon_status", Map.of("id", wagonStatus.getLong("id"), "dend", time));
        long id = db.insertRec("wagon_status", Map.of("wagon", wagon, "dbeg", time, "wagon_status_type", status));
        db.updateRec("wagon_info", Map.of("dbeg_status", time, "wagon_status_type", status), Map.of("wagon", wagon));
        return id;
    }

    /**
     * Сменить ответственных (факт): закрыть текущую запись факта журнала ответственных временем time и открыть новую с
     * сотрудниками usrs (пусто - не открывать), обновить кэш wagon_info. Время - позже начала (и конца, если её уже
     * закрыли) последней записи факта. Вызывается внутри транзакции.
     *
     * @param usrs ответственные; пусто или null - ответственный не назначен
     * @return открытая и закрытая записи
     * @throws ApiException 400 - время не позже последнего изменения ответственных
     */
    public Responsibles assign(long wagon, OffsetDateTime time, List<Long> usrs) throws Exception {
        return assign(wagon, time, usrs, null, null);
    }

    /**
     * Сменить ответственных (факт), как {@link #assign(long, OffsetDateTime, List)}, и отметить у открытой записи рейс.
     *
     * @param trip  рейс, в котором сменили ответственных; null - не рейс
     * @param point стадия рейса - WagonResponsibleTripPoint, если смену записала отметка рейса; null - не отметка
     */
    public Responsibles assign(long wagon, OffsetDateTime time, List<Long> usrs, Long trip, Integer point)
            throws Exception {
        DbRec last = db.loadSqlRec(WagonSql.lastResponsibleFact(),
                Map.of("wagon", wagon, "fact", WagonResponsibleKind.FACT));
        Long closed = null;
        if (last != null) {
            OffsetDateTime end = last.getDateTimeTZ("dend");
            if (!time.isAfter(last.getDateTimeTZ("dbeg")) || (end != null && time.isBefore(end))) {
                throw ApiException.badRequest("Время должно быть позже последнего изменения ответственных за вагон");
            }
            if (end == null) {
                db.updateRec("wagon_responsible", Map.of("id", last.getLong("id"), "dend", time));
                closed = last.getLong("id");
                evictRecord(closed);
            }
        }
        Long opened = openFact(wagon, time, usrs, trip, point);
        updateResponsibleInfo(wagon, opened);
        evictUsrs(usrSet(usrs));
        return new Responsibles(opened, closed);
    }

    /**
     * Сменить состав ответственных, назначенных документом (responsibles): у открытой документом записи - состав
     * usrs; пусто - запись удаляется. Документ снял всех, а теперь ответственные есть - открывается запись временем
     * time. Вызывается внутри транзакции.
     *
     * @return новые открытая и закрытая записи документа
     * @throws ApiException 409 - после документа ответственных уже меняли
     */
    public Responsibles reassign(long wagon, OffsetDateTime time, Responsibles responsibles, List<Long> usrs)
            throws Exception {
        Set<Long> set = usrSet(usrs);
        evictUsrs(set);
        Long opened = responsibles.opened();
        if (opened != null) {
            requireCurrent(opened);
            evictRecord(opened);
            if (set.isEmpty()) {
                db.deleteRec("wagon_responsible", opened);
                updateResponsibleInfo(wagon, null);
                return new Responsibles(null, responsibles.closed());
            }
            db.deleteRec("wagon_responsible_usr", Map.of("wagon_responsible", opened));
            insertUsrs(opened, set);
            return responsibles;
        }
        if (set.isEmpty()) {
            return responsibles;
        }
        DbRec last = db.loadSqlRec(WagonSql.lastResponsibleFact(),
                Map.of("wagon", wagon, "fact", WagonResponsibleKind.FACT));
        if (last != null && (last.get("dend") == null || last.getDateTimeTZ("dend").isAfter(time))) {
            throw ApiException.conflict("После этого у вагона меняли ответственных - сначала отмените это");
        }
        Long id = openFact(wagon, time, List.copyOf(set));
        updateResponsibleInfo(wagon, id);
        return new Responsibles(id, responsibles.closed());
    }

    /**
     * Отменить смену ответственных: открытая запись удаляется, закрытая снова открыта, кэш wagon_info - по ней.
     * Вызывается внутри транзакции.
     *
     * @throws ApiException 409 - после смены ответственных меняли снова
     */
    public void unassign(long wagon, Responsibles responsibles) throws Exception {
        if (responsibles.opened() != null) {
            requireCurrent(responsibles.opened());
            // кэш wagon_info ссылается на открытую запись: сначала отвязать его, иначе запись не удалить; ниже кэш
            // встанет на снова открытую закрытую запись
            updateResponsibleInfo(wagon, null);
            evictRecord(responsibles.opened());
            db.deleteRec("wagon_responsible", responsibles.opened());
        }
        Long closed = responsibles.closed();
        if (closed != null) {
            DbRec last = db.loadSqlRec(WagonSql.lastResponsibleFact(),
                    Map.of("wagon", wagon, "fact", WagonResponsibleKind.FACT));
            if (last == null || !Objects.equals(last.getLong("id"), closed)) {
                throw ApiException.conflict("После этого у вагона меняли ответственных - сначала отмените это");
            }
            Map<String, Object> values = new HashMap<>();
            values.put("id", closed);
            values.put("dend", null);
            db.updateRec("wagon_responsible", values);
            evictRecord(closed);
        } else if (responsibles.opened() == null) {
            return;
        }
        updateResponsibleInfo(wagon, closed);
    }

    /**
     * Текущие ответственные вагонов: открытые записи факта журнала ответственных и их состав.
     *
     * @return вагон - ответственные (usr) по ФИО; у вагона без ответственных - пустой список
     */
    public Map<Long, List<Long>> currentUsrs(List<Long> wagons) throws Exception {
        Map<Long, List<Long>> res = new HashMap<>();
        for (Long wagon : wagons) {
            res.put(wagon, new ArrayList<>());
        }
        if (wagons.isEmpty()) {
            return res;
        }
        for (DbRec r : db.loadSql(WagonSql.currentResponsibleUsrs(),
                Map.of("wagons", wagons, "fact", WagonResponsibleKind.FACT))) {
            res.get(r.getLong("wagon")).add(r.getLong("usr"));
        }
        return res;
    }

    // --- план ответственных: записи слоя «план»; план ищется по вагону, рейсу и стадии рейса

    /**
     * Задать план ответственных вагона на стадию point рейса trip (статус «ожидает»): есть такой - сменить состав, нет -
     * создать с началом dbeg (время стадии по плану рейса). Пустой состав - план «ответственных нет». Вызывается внутри
     * транзакции.
     */
    public void setPlan(long wagon, long trip, int point, OffsetDateTime dbeg, List<Long> usrs) throws Exception {
        evictUsrs(usrSet(usrs));
        DbRec plan = waitingPlan(wagon, trip, point);
        if (plan != null) {
            evictRecord(plan.getLong("id"));
            db.deleteRec("wagon_responsible_usr", Map.of("wagon_responsible", plan.getLong("id")));
            insertUsrs(plan.getLong("id"), usrSet(usrs));
            return;
        }
        Map<String, Object> values = new HashMap<>();
        values.put("wagon", wagon);
        values.put("kind", WagonResponsibleKind.PLAN);
        values.put("plan_status", WagonResponsiblePlanStatus.WAITING);
        values.put("trip", trip);
        values.put("trip_point", point);
        values.put("dbeg", dbeg);
        values.put("created_by_usr", currentUserService.get().id());
        values.put("created_dt", OffsetDateTime.now());
        insertUsrs(db.insertRec("wagon_responsible", values), usrSet(usrs));
    }

    /** Отменить ожидающий план вагона на стадию point рейса trip; нет - ничего. Вызывается внутри транзакции. */
    public void cancelPlan(long wagon, long trip, int point) throws Exception {
        switchPlan(wagon, trip, point, WagonResponsiblePlanStatus.WAITING, WagonResponsiblePlanStatus.CANCELLED);
    }

    /**
     * Ожидающий план вагона на стадию point рейса trip и его состав; нет - null.
     *
     * @return состав плана; пустой - план «ответственных нет»
     */
    public List<Long> waitingPlanUsrs(long wagon, long trip, int point) throws Exception {
        DbRec plan = waitingPlan(wagon, trip, point);
        return plan == null ? null : usrsOf(plan.getLong("id"));
    }

    /** Перевести план вагона на стадию point рейса trip из status в newStatus; нет такого - ничего. */
    public void switchPlan(long wagon, long trip, int point, int status, int newStatus) throws Exception {
        DbRec plan = db.loadRec("wagon_responsible", Map.of("wagon", wagon, "kind", WagonResponsibleKind.PLAN,
                "plan_status", status, "trip", trip, "trip_point", point));
        if (plan != null) {
            db.updateRec("wagon_responsible", Map.of("id", plan.getLong("id"), "plan_status", newStatus));
            evictRecord(plan.getLong("id"));
        }
    }

    /**
     * Отменить смену ответственных (факт), которую записала отметка стадии point рейса trip временем time: открытая
     * отметкой запись удаляется, закрытая ею - снова открыта. Вызывается внутри транзакции.
     *
     * @throws ApiException 409 - после этого ответственных меняли снова
     */
    public void unassignMark(long wagon, long trip, int point, OffsetDateTime time) throws Exception {
        DbRec opened = markFact(wagon, trip, point);
        DbRec closed = db.loadRec("wagon_responsible", Map.of("wagon", wagon, "kind", WagonResponsibleKind.FACT,
                "dend", time));
        unassign(wagon, new Responsibles(opened == null ? null : opened.getLong("id"),
                closed == null ? null : closed.getLong("id")));
    }

    /**
     * Перенести время смены ответственных (факт), которую записала отметка стадии point рейса trip временем from:
     * открытая отметкой запись начинается, а закрытая ею заканчивается временем to. Вызывается внутри транзакции.
     *
     * @throws ApiException 400 - время не между прежней сменой и следующей
     */
    public void retimeMark(long wagon, long trip, int point, OffsetDateTime from, OffsetDateTime to)
            throws Exception {
        DbRec opened = markFact(wagon, trip, point);
        DbRec closed = db.loadRec("wagon_responsible", Map.of("wagon", wagon, "kind", WagonResponsibleKind.FACT,
                "dend", from));
        if ((closed != null && !to.isAfter(closed.getDateTimeTZ("dbeg")))
                || (opened != null && opened.get("dend") != null && !to.isBefore(opened.getDateTimeTZ("dend")))) {
            throw ApiException.badRequest("Время должно быть между прежней и следующей сменой ответственных за вагон");
        }
        if (closed != null) {
            db.updateRec("wagon_responsible", Map.of("id", closed.getLong("id"), "dend", to));
        }
        if (opened != null) {
            db.updateRec("wagon_responsible", Map.of("id", opened.getLong("id"), "dbeg", to));
        }
    }

    private DbRec waitingPlan(long wagon, long trip, int point) throws Exception {
        return db.loadRec("wagon_responsible", Map.of("wagon", wagon, "kind", WagonResponsibleKind.PLAN,
                "plan_status", WagonResponsiblePlanStatus.WAITING, "trip", trip, "trip_point", point));
    }

    /** Запись факта, которую открыла отметка стадии point рейса trip; отметка сняла всех или не меняла - null. */
    private DbRec markFact(long wagon, long trip, int point) throws Exception {
        return db.loadRec("wagon_responsible", Map.of("wagon", wagon, "kind", WagonResponsibleKind.FACT,
                "trip", trip, "trip_point", point));
    }

    private List<Long> usrsOf(long record) throws Exception {
        return db.loadSql(WagonSql.responsibleUsrs(), Map.of("records", List.of(record))).stream()
                .map(r -> r.getLong("usr")).toList();
    }

    /** Одинаков ли состав ответственных: порядок не важен. */
    public static boolean sameUsrs(List<Long> a, List<Long> b) {
        return usrSet(a).equals(usrSet(b));
    }

    private Long openFact(long wagon, OffsetDateTime time, List<Long> usrs) throws Exception {
        return openFact(wagon, time, usrs, null, null);
    }

    private Long openFact(long wagon, OffsetDateTime time, List<Long> usrs, Long trip, Integer point)
            throws Exception {
        Set<Long> set = usrSet(usrs);
        if (set.isEmpty()) {
            return null;
        }
        Map<String, Object> values = new HashMap<>();
        values.put("wagon", wagon);
        values.put("kind", WagonResponsibleKind.FACT);
        values.put("trip", trip);
        values.put("trip_point", point);
        values.put("dbeg", time);
        values.put("created_by_usr", currentUserService.get().id());
        values.put("created_dt", OffsetDateTime.now());
        long id = db.insertRec("wagon_responsible", values);
        insertUsrs(id, set);
        return id;
    }

    private void insertUsrs(long record, Set<Long> usrs) throws Exception {
        for (Long usr : usrs) {
            db.insertRec("wagon_responsible_usr", Map.of("wagon_responsible", record, "usr", usr));
        }
    }

    private void requireCurrent(long record) throws Exception {
        DbRec rec = db.loadRec("wagon_responsible", record);
        if (rec == null || rec.get("dend") != null) {
            throw ApiException.conflict("После этого у вагона меняли ответственных - сначала отмените это");
        }
    }

    /**
     * Сбросить кэш текущего пользователя у состава записи: вагоны сотрудника на рейс берутся из журнала
     * ответственных.
     */
    private void evictRecord(long record) throws Exception {
        evictUsrs(usrsOf(record));
    }

    private void evictUsrs(Iterable<Long> usrs) {
        usrs.forEach(currentUserService::evict);
    }

    private void updateResponsibleInfo(long wagon, Long record) throws Exception {
        Map<String, Object> info = new HashMap<>();
        info.put("wagon_responsible", record);
        db.updateRec("wagon_info", info, Map.of("wagon", wagon));
    }

    private static Set<Long> usrSet(List<Long> usrs) {
        Set<Long> res = new LinkedHashSet<>();
        if (usrs != null) {
            usrs.stream().filter(Objects::nonNull).forEach(res::add);
        }
        return res;
    }

    /**
     * Перевести вагон в новое расположение; состояние не меняется. Вызывается внутри транзакции.
     *
     * @param station станция, если расположение - станция
     * @param depot   депо, если расположение - депо
     * @param yard    парк отстоя, если расположение - парк отстоя
     * @return wagon_location.id открытой записи
     */
    public long moveLocation(long wagon, OffsetDateTime time, long locationType, Long station, Long depot, Long yard)
            throws Exception {
        long id = openLocation(wagon, time, locationType, station, depot, yard, null);
        db.updateRec("wagon_info", locationInfo(time, locationType, station, depot, yard, null), Map.of("wagon", wagon));
        return id;
    }

    /**
     * Перенести время перевода: записи расположения и состояния, открытые переводом (opened), начинаются со временем
     * time, а прежние записи вагона заканчиваются им; ответственных не меняет. Время должно остаться между прежней записью и следующей. Вызывается внутри
     * транзакции.
     *
     * @throws ApiException 400 - время не между соседними записями
     */
    public void retime(Opened opened, OffsetDateTime time) throws Exception {
        retimeRec("wagon_location", opened.location(), time, "dbeg_location");
        retimeRec("wagon_status", opened.status(), time, "dbeg_status");
    }

    /**
     * Отменить перевод: записи журналов, открытые переводом (opened), удаляются, прежние снова текущие, кэш
     * wagon_info - по ним. После перевода у вагона не должно быть других записей. Вызывается внутри транзакции.
     */
    public void undo(Opened opened) throws Exception {
        long wagon = db.loadRec("wagon_location", opened.location()).getLong("wagon");
        DbRec location = undoLocationRec(opened.location());
        DbRec status = undoRec("wagon_status", opened.status());
        Map<String, Object> info = locationInfo(location);
        info.put("dbeg_status", status.getDateTimeTZ("dbeg"));
        info.put("wagon_status_type", status.getLong("wagon_status_type"));
        db.updateRec("wagon_info", info, Map.of("wagon", wagon));
        if (opened.responsibles() != null) {
            unassign(wagon, opened.responsibles());
        }
    }

    /**
     * Отменить перевод в новое расположение (moveLocation): запись журнала opened удаляется, прежняя снова текущая,
     * кэш wagon_info - по ней. После перевода у вагона не должно быть других записей расположения. Вызывается внутри
     * транзакции.
     */
    public void undoLocation(long opened) throws Exception {
        long wagon = db.loadRec("wagon_location", opened).getLong("wagon");
        db.updateRec("wagon_info", locationInfo(undoLocationRec(opened)), Map.of("wagon", wagon));
    }

    /**
     * Удалить запись расположения и снова сделать текущей прежнюю. Возвращает прежнюю запись; null - запись была
     * первой: расположение снова не задано.
     */
    private DbRec undoLocationRec(long id) throws Exception {
        DbRec rec = db.loadRec("wagon_location", id);
        if (rec.getDateTimeTZ("dend") == null && previous("wagon_location", rec) == null) {
            db.deleteRec("wagon_location", id);
            return null;
        }
        return undoRec("wagon_location", id);
    }

    private void retimeRec(String table, long id, OffsetDateTime time, String infoField) throws Exception {
        DbRec rec = db.loadRec(table, id);
        DbRec prev = previous(table, rec);
        OffsetDateTime end = rec.getDateTimeTZ("dend");
        if ((prev != null && !time.isAfter(prev.getDateTimeTZ("dbeg"))) || (end != null && !time.isBefore(end))) {
            throw ApiException.badRequest("Время должно быть между прежним и следующим изменением вагонов");
        }
        db.updateRec(table, Map.of("id", id, "dbeg", time));
        if (prev != null) {
            db.updateRec(table, Map.of("id", prev.getLong("id"), "dend", time));
        }
        if (end == null) {
            db.updateRec("wagon_info", Map.of(infoField, time), Map.of("wagon", rec.getLong("wagon")));
        }
    }

    /**
     * Удалить запись журнала и снова сделать текущей прежнюю. Возвращает прежнюю запись.
     *
     * @throws ApiException 409 - после записи есть другие или прежней нет
     */
    private DbRec undoRec(String table, long id) throws Exception {
        DbRec rec = db.loadRec(table, id);
        DbRec prev = previous(table, rec);
        if (rec.getDateTimeTZ("dend") != null || prev == null) {
            throw ApiException.conflict("После этого у вагона были другие изменения - сначала отмените их");
        }
        db.deleteRec(table, id);
        Map<String, Object> values = new HashMap<>();
        values.put("id", prev.getLong("id"));
        values.put("dend", null);
        db.updateRec(table, values);
        prev.put("dend", null);
        return prev;
    }

    /** Запись журнала вагона, которая закончилась началом rec; нет - null. */
    private DbRec previous(String table, DbRec rec) throws Exception {
        return db.loadRec(table, Map.of("wagon", rec.getLong("wagon"), "dend", rec.getDateTimeTZ("dbeg")));
    }

    private long openLocation(long wagon, OffsetDateTime time, long locationType, Long station, Long depot, Long yard,
                              Long trip) throws Exception {
        DbRec location = db.loadRec("wagon_location", current(wagon));
        // текущей записи нет - расположение не задано: запись будет первой
        if (location != null) {
            if (!time.isAfter(location.getDateTimeTZ("dbeg"))) {
                throw ApiException.badRequest("Время должно быть позже последнего изменения вагонов");
            }
            db.updateRec("wagon_location", Map.of("id", location.getLong("id"), "dend", time));
        }
        Map<String, Object> values = new HashMap<>(locationValues(locationType, station, depot, yard, trip));
        values.values().removeIf(Objects::isNull);
        values.put("wagon", wagon);
        values.put("dbeg", time);
        return db.insertRec("wagon_location", values);
    }

    /** Расположение для кэша wagon_info по записи журнала; null - расположение не задано. */
    private static Map<String, Object> locationInfo(DbRec location) {
        if (location == null) {
            Map<String, Object> info = new HashMap<>();
            for (String field : List.of("location_type", "station", "depot", "yard", "trip", "dbeg_location")) {
                info.put(field, null);
            }
            return info;
        }
        return locationInfo(location.getDateTimeTZ("dbeg"), location.getLong("location_type"),
                location.getLong("station"), location.getLong("depot"), location.getLong("yard"),
                location.getLong("trip"));
    }

    private static Map<String, Object> locationInfo(OffsetDateTime time, long locationType, Long station, Long depot,
                                                    Long yard, Long trip) {
        Map<String, Object> info = locationValues(locationType, station, depot, yard, trip);
        info.put("dbeg_location", time);
        return info;
    }

    private static Map<String, Object> locationValues(long locationType, Long station, Long depot, Long yard, Long trip) {
        Map<String, Object> values = new HashMap<>();
        values.put("location_type", locationType);
        values.put("station", station);
        values.put("depot", depot);
        values.put("yard", yard);
        values.put("trip", trip);
        return values;
    }

    private static Map<String, Object> current(long wagon) {
        Map<String, Object> params = new HashMap<>();
        params.put("wagon", wagon);
        params.put("dend", null);
        return params;
    }

}
