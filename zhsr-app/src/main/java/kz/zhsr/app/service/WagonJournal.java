package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Журналы вагона: перевод в новое расположение и состояние.
 */
@Service
public class WagonJournal {

    /**
     * Записи журналов, открытые переводом.
     *
     * @param location wagon_location.id
     * @param status   wagon_status.id
     */
    public record Opened(long location, long status) {
    }

    private final Db db;

    public WagonJournal(Db db) {
        this.db = db;
    }

    /**
     * Перевести вагон: закрыть текущие записи журналов временем time, открыть новые, обновить кэш wagon_info.
     * Вызывается внутри транзакции.
     *
     * @param station станция, если расположение - станция
     * @param trip    рейс, если расположение - рейс
     */
    public Opened move(long wagon, OffsetDateTime time, long locationType, Long station, Long trip, long status)
            throws Exception {
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
        return new Opened(locationId, statusId);
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
     * Перенести время перевода: записи журналов, открытые переводом (opened), начинаются со временем time, а прежние
     * записи вагона заканчиваются им. Время должно остаться между прежней записью и следующей. Вызывается внутри
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
        DbRec location = undoRec("wagon_location", opened.location());
        DbRec status = undoRec("wagon_status", opened.status());
        Map<String, Object> info = locationInfo(location.getDateTimeTZ("dbeg"), location.getLong("location_type"),
                location.getLong("station", null), location.getLong("depot", null), location.getLong("yard", null),
                location.getLong("trip", null));
        info.put("dbeg_status", status.getDateTimeTZ("dbeg"));
        info.put("wagon_status_type", status.getLong("wagon_status_type"));
        db.updateRec("wagon_info", info, Map.of("wagon", location.getLong("wagon")));
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
            throw ApiException.conflict("У вагона после отметки есть другие изменения");
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
        if (location == null) {
            throw ApiException.conflict("У вагона нет текущего расположения");
        }
        if (!time.isAfter(location.getDateTimeTZ("dbeg"))) {
            throw ApiException.badRequest("Время должно быть позже последнего изменения вагонов");
        }
        db.updateRec("wagon_location", Map.of("id", location.getLong("id"), "dend", time));
        Map<String, Object> values = new HashMap<>(locationValues(locationType, station, depot, yard, trip));
        values.values().removeIf(Objects::isNull);
        values.put("wagon", wagon);
        values.put("dbeg", time);
        return db.insertRec("wagon_location", values);
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
