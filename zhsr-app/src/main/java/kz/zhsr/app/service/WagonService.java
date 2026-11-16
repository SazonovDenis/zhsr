package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.TrainWagonChangeType;
import kz.zhsr.app.dto.ref.CheckTemplateRef;
import kz.zhsr.app.dto.ref.EquipTemplateRef;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.wagon.WagonJournalRow;
import kz.zhsr.app.dto.wagon.WagonPlace;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Журнал вагона (Ф-ВАГ-05): история расположения, состояния и ответственного с основаниями.
 */
@Service
public class WagonService {

    /**
     * Документ, который открыл запись журнала.
     *
     * @param type   константа WagonJournalRow.BASIS_*
     * @param id     id документа: акта, прицепки или отцепки (train_wagon_change) или рейса
     * @param number номер акта; null - не акт
     * @param trip   рейс; null - нет
     * @param byUsr  кто провёл документ; null - не отмечено
     * @param reason    причина из документа; null - нет
     * @param canCancel текущий сотрудник может отменить документ: акт (Ф-АКТ-06), прицепку или отцепку (Ф-АКТ-01)
     */
    private record Basis(int type, long id, String number, Long trip, Long byUsr, String reason, boolean canCancel) {
    }

    /** Событие журнала: записи расположения, состояния и ответственного, открытые одним документом в одно время. */
    private static final class Event {
        OffsetDateTime dbeg;
        Basis basis;
        DbRec location;
        DbRec status;
        DbRec responsible;
        /** проверка (check_list) или опись (equip_list) вагона; null - событие журналов */
        DbRec document;
        /** число нарушений документа */
        int violations;
    }

    private final Db db;
    private final CurrentUserService currentUserService;
    private final TrainService trainService;
    private final RefResolver refResolver;

    public WagonService(Db db, CurrentUserService currentUserService, TrainService trainService,
                        RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.trainService = trainService;
        this.refResolver = refResolver;
    }

    /**
     * Журнал вагона за период (GET /wagons/{id}/journal): события с dbeg в [from, to), новые первыми.
     *
     * @param from начало периода; null - без начала
     * @param to   конец периода, не включая; null - без конца
     */
    public List<WagonJournalRow> journal(long wagon, OffsetDateTime from, OffsetDateTime to) throws Exception {
        if (db.loadRec("wagon", Map.of("id", wagon)) == null) {
            throw ApiException.notFound("Вагон не найден");
        }
        List<DbRec> locations = db.loadSql(WagonSql.locations(), Map.of("wagon", wagon));
        List<DbRec> statuses = db.loadSql(WagonSql.statuses(), Map.of("wagon", wagon));
        List<DbRec> responsibles = db.loadSql(WagonSql.responsibles(), Map.of("wagon", wagon));
        Map<Long, Basis> locationBasis = new HashMap<>();
        Map<Long, Basis> statusBasis = new HashMap<>();
        Map<Long, Basis> responsibleBasis = new HashMap<>();
        loadBases(wagon, locations, statuses, responsibles, locationBasis, statusBasis, responsibleBasis);

        // события по времени и документу: перевод открывает записи обоих журналов одним временем
        Map<String, Event> events = new LinkedHashMap<>();
        for (DbRec rec : locations) {
            event(events, rec, locationBasis.get(rec.getLong("id"))).location = rec;
        }
        for (DbRec rec : statuses) {
            event(events, rec, statusBasis.get(rec.getLong("id"))).status = rec;
        }
        for (DbRec rec : responsibles) {
            Basis basis = responsibleBasis.get(rec.getLong("id"));
            // первая запись «не назначен» без документа (ввод вагона) отдельной строкой не показывается
            if (basis == null && previous(responsibles, rec) == null && rec.get("usr") == null
                    && !events.containsKey(key(rec.getDateTimeTZ("dbeg"), null))) {
                continue;
            }
            event(events, rec, basis).responsible = rec;
        }

        documents(events, wagon, "check_list", WagonJournalRow.BASIS_CHECK);
        documents(events, wagon, "equip_list", WagonJournalRow.BASIS_EQUIP);

        List<WagonJournalRow> res = new ArrayList<>();
        for (Event e : events.values()) {
            if ((from != null && e.dbeg.isBefore(from)) || (to != null && !e.dbeg.isBefore(to))) {
                continue;
            }
            res.add(row(e, locations, statuses, responsibles));
        }
        res.sort((a, b) -> b.getDbeg().compareTo(a.getDbeg()));
        refResolver.resolve(res);
        return res;
    }

    /**
     * События проверок по чек-листу и описей экипировки вагона: время - начало документа; расположение и состояние
     * документ не меняет.
     *
     * @param table check_list или equip_list
     */
    private void documents(Map<String, Event> events, long wagon, String table, int type) throws Exception {
        List<DbRec> docs = db.loadList(table, Map.of("wagon", wagon));
        if (docs.isEmpty()) {
            return;
        }
        Map<Long, List<DbRec>> violations = UtDb.collectGroupBy_records(db.loadList(table + "_value",
                Map.of(table, docs.stream().map(d -> d.getLong("id")).toList(), "violation", true)), table);
        for (DbRec doc : docs) {
            Basis basis = new Basis(type, doc.getLong("id"), null, doc.getLong("trip", null),
                    doc.getLong("created_by_usr"), null, false);
            DbRec time = new DbRec();
            time.put("dbeg", doc.get("created_dt"));
            Event e = event(events, time, basis);
            e.document = doc;
            e.violations = violations.getOrDefault(doc.getLong("id"), List.of()).size();
        }
    }

    private static String key(OffsetDateTime dbeg, Basis basis) {
        return dbeg.toInstant() + "|" + (basis == null ? "" : basis.type() + ":" + basis.id());
    }

    private static Event event(Map<String, Event> events, DbRec rec, Basis basis) {
        OffsetDateTime dbeg = rec.getDateTimeTZ("dbeg");
        return events.computeIfAbsent(key(dbeg, basis), k -> {
            Event e = new Event();
            e.dbeg = dbeg;
            e.basis = basis;
            return e;
        });
    }

    private static WagonJournalRow row(Event e, List<DbRec> locations, List<DbRec> statuses,
                                       List<DbRec> responsibles) {
        WagonJournalRow row = new WagonJournalRow();
        row.setDbeg(e.dbeg);

        DbRec location = e.location != null ? e.location : actual(locations, e.dbeg);
        DbRec prevLocation = e.location != null ? previous(locations, e.location) : null;
        row.setLocation(location == null ? null : place(location));
        row.setPrevLocation(prevLocation == null ? null : place(prevLocation));
        row.setLocationChanged(e.location != null);

        DbRec status = e.status != null ? e.status : actual(statuses, e.dbeg);
        DbRec prevStatus = e.status != null ? previous(statuses, e.status) : null;
        row.setWagonStatusType(status == null ? 0 : status.getLong("wagon_status_type"));
        row.setPrevWagonStatusType(prevStatus == null ? null : prevStatus.getLong("wagon_status_type"));
        row.setStatusChanged(e.status != null);

        DbRec responsible = e.responsible != null ? e.responsible : actual(responsibles, e.dbeg);
        DbRec prevResponsible = e.responsible != null ? previous(responsibles, e.responsible) : null;
        row.setResponsibleUsr(userRef(responsible));
        row.setPrevResponsibleUsr(userRef(prevResponsible));
        row.setResponsibleChanged(e.responsible != null);

        Basis basis = e.basis;
        if (basis == null) {
            // без документа: первая запись журнала - ввод вагона, иначе ручная правка
            boolean first = (e.location == null || previous(locations, e.location) == null)
                    && (e.status == null || previous(statuses, e.status) == null)
                    && (e.responsible == null || previous(responsibles, e.responsible) == null);
            row.setBasisType(first ? WagonJournalRow.BASIS_ENTRY : WagonJournalRow.BASIS_MANUAL);
            return row;
        }
        row.setBasisType(basis.type());
        row.setActNumber(basis.number());
        row.setBasisTrip(basis.trip() == null ? null : new TripRef(basis.trip()));
        row.setByUsr(basis.byUsr() == null ? null : new UserRef(basis.byUsr()));
        row.setComment(basis.reason());
        if (basis.type() == WagonJournalRow.BASIS_TRANSFER || basis.type() == WagonJournalRow.BASIS_DETACH
                || basis.type() == WagonJournalRow.BASIS_ATTACH) {
            row.setAct(basis.id());
            row.setCanCancel(basis.canCancel());
        }
        if (e.document != null) {
            row.setViolationCount(e.violations);
            if (basis.type() == WagonJournalRow.BASIS_CHECK) {
                row.setCheckList(basis.id());
                row.setCheckListTemplate(new CheckTemplateRef(e.document.getLong("check_list_template")));
            } else {
                row.setEquipList(basis.id());
                row.setEquipListTemplate(new EquipTemplateRef(e.document.getLong("equip_list_template")));
            }
        }
        return row;
    }

    /** Ответственный записи журнала; записи нет или ответственный не назначен - null. */
    private static UserRef userRef(DbRec rec) {
        Long usr = rec == null ? null : rec.getLong("usr", null);
        return usr == null ? null : new UserRef(usr);
    }

    /** Запись журнала, действующая в момент time; null - вагона ещё не было. */
    private static DbRec actual(List<DbRec> records, OffsetDateTime time) {
        DbRec res = null;
        for (DbRec rec : records) {
            if (rec.getDateTimeTZ("dbeg").isAfter(time)) {
                break;
            }
            res = rec;
        }
        return res;
    }

    /** Предыдущая запись журнала; null - запись первая. Записи упорядочены по dbeg. */
    private static DbRec previous(List<DbRec> records, DbRec rec) {
        int i = records.indexOf(rec);
        return i > 0 ? records.get(i - 1) : null;
    }

    private static WagonPlace place(DbRec rec) {
        WagonPlace place = new WagonPlace();
        place.setLocationType(rec.getLong("location_type"));
        place.setPlace(rec.getLong("place"));
        Long trip = rec.getLong("trip", null);
        place.setTrip(trip == null ? null : new TripRef(trip));
        return place;
    }

    /**
     * Основания записей журналов: прицепки и отцепки в рейсе, акты приёма-передачи, ручные правки, отправление и
     * прибытие рейсов. Отметка отправления (прибытия) ссылок не хранит: её записи - записи с началом во время отметки.
     */
    private void loadBases(long wagon, List<DbRec> locations, List<DbRec> statuses, List<DbRec> responsibles,
                           Map<Long, Basis> locationBasis, Map<Long, Basis> statusBasis,
                           Map<Long, Basis> responsibleBasis) throws Exception {
        CurrentUser me = currentUserService.get();
        Map<String, Object> params = Map.of("wagon", wagon);
        for (DbRec rec : db.loadSql(WagonSql.changes(), params)) {
            int type = rec.getInt("change_type") == TrainWagonChangeType.DETACH
                    ? WagonJournalRow.BASIS_DETACH
                    : WagonJournalRow.BASIS_ATTACH;
            boolean canCancel = trainService.canUndo(me, rec.getLong("id"));
            Basis basis = new Basis(type, rec.getLong("id"), null, rec.getLong("trip", null),
                    rec.getLong("usr", null), rec.get("comment") == null ? null : rec.getString("comment"),
                    canCancel);
            put(locationBasis, rec.getLong("wagon_location", null), basis);
            put(statusBasis, rec.getLong("wagon_status", null), basis);
            put(responsibleBasis, rec.getLong("wagon_responsible", null), basis);
        }
        for (DbRec rec : db.loadSql(WagonSql.transferActs(), params)) {
            // приём (расположение) провёл принявший, назначение ответственного при оформлении - сдавший
            boolean canCancel = ActCancelService.canCancelTransfer(me, rec.getLong("act_status_type"),
                    rec.getLong("transferred_by_usr"), rec.getLong("received_by_usr", null));
            Basis received = new Basis(WagonJournalRow.BASIS_TRANSFER, rec.getLong("id"), rec.getString("number"),
                    null, rec.getLong("received_by_usr", null), null, canCancel);
            Basis transferred = new Basis(WagonJournalRow.BASIS_TRANSFER, rec.getLong("id"), rec.getString("number"),
                    null, rec.getLong("transferred_by_usr", null), null, canCancel);
            put(locationBasis, rec.getLong("wagon_location", null), received);
            put(responsibleBasis, rec.getLong("wagon_responsible", null), transferred);
        }
        for (DbRec rec : db.loadSql(WagonSql.edits(), params)) {
            Basis basis = new Basis(WagonJournalRow.BASIS_MANUAL, rec.getLong("id"), null, null,
                    rec.getLong("created_by_usr"), rec.get("comment") == null ? null : rec.getString("comment"), false);
            put(locationBasis, rec.getLong("wagon_location", null), basis);
            put(statusBasis, rec.getLong("wagon_status", null), basis);
            put(responsibleBasis, rec.getLong("wagon_responsible", null), basis);
        }
        markBases(locations, statuses, responsibles, locationBasis, statusBasis, responsibleBasis);
    }

    /**
     * Основания отметок отправления и прибытия: запись расположения «в рейсе» с началом во время отправления рейса -
     * отправление; запись после расположения «в рейсе» с началом во время прибытия рейса - прибытие. Записи состояния и
     * ответственного с тем же началом и без другого основания - та же отметка.
     */
    private void markBases(List<DbRec> locations, List<DbRec> statuses, List<DbRec> responsibles,
                           Map<Long, Basis> locationBasis, Map<Long, Basis> statusBasis,
                           Map<Long, Basis> responsibleBasis) throws Exception {
        List<Long> tripIds = locations.stream().map(r -> r.getLong("trip", null)).filter(t -> t != null)
                .distinct().toList();
        if (tripIds.isEmpty()) {
            return;
        }
        Map<Long, DbRec> trips = UtDb.collectGroupBy_record(db.loadList("trip", Map.of("id", tripIds)), "id");
        Map<OffsetDateTime, Basis> byTime = new HashMap<>();
        for (int i = 0; i < locations.size(); i++) {
            DbRec rec = locations.get(i);
            Long tripId = rec.getLong("trip", null);
            if (rec.getLong("location_type") != LocationType.TRIP || tripId == null) {
                continue;
            }
            DbRec trip = trips.get(tripId);
            OffsetDateTime departed = trip.getDateTimeTZ("departed_dt");
            if (departed != null && departed.isEqual(rec.getDateTimeTZ("dbeg"))) {
                Basis departure = new Basis(WagonJournalRow.BASIS_DEPARTURE, tripId, null, tripId,
                        trip.getLong("departed_by_usr", null), null, false);
                locationBasis.putIfAbsent(rec.getLong("id"), departure);
                byTime.put(departed.toInstant().atOffset(ZoneOffset.UTC), departure);
            }
            OffsetDateTime arrived = trip.getDateTimeTZ("arrived_dt");
            if (arrived != null && i + 1 < locations.size()
                    && arrived.isEqual(locations.get(i + 1).getDateTimeTZ("dbeg"))) {
                Basis arrival = new Basis(WagonJournalRow.BASIS_ARRIVAL, tripId, null, tripId,
                        trip.getLong("arrived_by_usr", null), null, false);
                locationBasis.putIfAbsent(locations.get(i + 1).getLong("id"), arrival);
                byTime.put(arrived.toInstant().atOffset(ZoneOffset.UTC), arrival);
            }
        }
        for (DbRec rec : statuses) {
            Basis basis = byTime.get(rec.getDateTimeTZ("dbeg").toInstant().atOffset(ZoneOffset.UTC));
            if (basis != null) {
                statusBasis.putIfAbsent(rec.getLong("id"), basis);
            }
        }
        for (DbRec rec : responsibles) {
            Basis basis = byTime.get(rec.getDateTimeTZ("dbeg").toInstant().atOffset(ZoneOffset.UTC));
            if (basis != null) {
                responsibleBasis.putIfAbsent(rec.getLong("id"), basis);
            }
        }
    }

    private static void put(Map<Long, Basis> map, Long record, Basis basis) {
        if (record != null) {
            map.put(record, basis);
        }
    }

}
