package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.constants.CouplingType;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.wagon.WagonJournalRow;
import kz.zhsr.app.dto.wagon.WagonPlace;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Журнал вагона (Ф-ВАГ-05): история расположения и состояния с основаниями.
 */
@Service
public class WagonService {

    /**
     * Документ, который открыл запись журнала.
     *
     * @param type   константа WagonJournalRow.BASIS_*
     * @param id     id документа: акта или рейса
     * @param number номер акта; null - не акт
     * @param trip   рейс; null - нет
     * @param byUsr  кто провёл документ; null - не отмечено
     * @param reason причина из документа; null - нет
     */
    private record Basis(int type, long id, String number, Long trip, Long byUsr, String reason) {
    }

    /** Событие журнала: записи расположения и состояния, открытые одним документом в одно время. */
    private static final class Event {
        OffsetDateTime dbeg;
        Basis basis;
        DbRec location;
        DbRec status;
    }

    private final Db db;
    private final RefResolver refResolver;

    public WagonService(Db db, RefResolver refResolver) {
        this.db = db;
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
        Map<Long, Basis> locationBasis = new HashMap<>();
        Map<Long, Basis> statusBasis = new HashMap<>();
        loadBases(wagon, locationBasis, statusBasis);

        // события по времени и документу: перевод открывает записи обоих журналов одним временем
        Map<String, Event> events = new LinkedHashMap<>();
        for (DbRec rec : locations) {
            event(events, rec, locationBasis.get(rec.getLong("id"))).location = rec;
        }
        for (DbRec rec : statuses) {
            event(events, rec, statusBasis.get(rec.getLong("id"))).status = rec;
        }

        List<WagonJournalRow> res = new ArrayList<>();
        for (Event e : events.values()) {
            if ((from != null && e.dbeg.isBefore(from)) || (to != null && !e.dbeg.isBefore(to))) {
                continue;
            }
            res.add(row(e, locations, statuses));
        }
        res.sort((a, b) -> b.getDbeg().compareTo(a.getDbeg()));
        refResolver.resolve(res);
        return res;
    }

    private static Event event(Map<String, Event> events, DbRec rec, Basis basis) {
        OffsetDateTime dbeg = rec.getDateTimeTZ("dbeg");
        String key = dbeg.toInstant() + "|" + (basis == null ? "" : basis.type() + ":" + basis.id());
        return events.computeIfAbsent(key, k -> {
            Event e = new Event();
            e.dbeg = dbeg;
            e.basis = basis;
            return e;
        });
    }

    private static WagonJournalRow row(Event e, List<DbRec> locations, List<DbRec> statuses) {
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

        Basis basis = e.basis;
        if (basis == null) {
            // без документа: первая запись журнала - ввод вагона, иначе ручная правка
            boolean first = (e.location == null || previous(locations, e.location) == null)
                    && (e.status == null || previous(statuses, e.status) == null);
            row.setBasisType(first ? WagonJournalRow.BASIS_ENTRY : WagonJournalRow.BASIS_MANUAL);
            return row;
        }
        row.setBasisType(basis.type());
        row.setActNumber(basis.number());
        row.setBasisTrip(basis.trip() == null ? null : new TripRef(basis.trip()));
        row.setByUsr(basis.byUsr() == null ? null : new UserRef(basis.byUsr()));
        row.setComment(basis.reason());
        return row;
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

    /** Основания записей журналов: акты отцепки и прицепки, акты приёма-передачи, отправление и прибытие рейсов. */
    private void loadBases(long wagon, Map<Long, Basis> locationBasis, Map<Long, Basis> statusBasis) throws Exception {
        Map<String, Object> params = Map.of("wagon", wagon);
        for (DbRec rec : db.loadSql(WagonSql.couplingActs(), params)) {
            int type = rec.getLong("coupling_type") == CouplingType.DETACH
                    ? WagonJournalRow.BASIS_DETACH
                    : WagonJournalRow.BASIS_ATTACH;
            Basis basis = new Basis(type, rec.getLong("id"), rec.getString("number"), rec.getLong("trip"),
                    rec.getLong("created_by_usr", null), rec.get("reason") == null ? null : rec.getString("reason"));
            put(locationBasis, rec.getLong("wagon_location", null), basis);
            put(statusBasis, rec.getLong("wagon_status", null), basis);
        }
        for (DbRec rec : db.loadSql(WagonSql.transferActs(), params)) {
            Basis basis = new Basis(WagonJournalRow.BASIS_TRANSFER, rec.getLong("id"), rec.getString("number"), null,
                    rec.getLong("received_by_usr", null), null);
            put(locationBasis, rec.getLong("wagon_location", null), basis);
        }
        for (DbRec rec : db.loadSql(WagonSql.tripWagons(), params)) {
            long trip = rec.getLong("trip");
            Basis departure = new Basis(WagonJournalRow.BASIS_DEPARTURE, trip, null, trip,
                    rec.getLong("departed_by_usr", null), null);
            Basis arrival = new Basis(WagonJournalRow.BASIS_ARRIVAL, trip, null, trip,
                    rec.getLong("arrived_by_usr", null), null);
            put(locationBasis, rec.getLong("depart_wagon_location", null), departure);
            put(statusBasis, rec.getLong("depart_wagon_status", null), departure);
            put(locationBasis, rec.getLong("arrive_wagon_location", null), arrival);
            put(statusBasis, rec.getLong("arrive_wagon_status", null), arrival);
        }
    }

    private static void put(Map<Long, Basis> map, Long record, Basis basis) {
        if (record != null) {
            map.put(record, basis);
        }
    }

}
