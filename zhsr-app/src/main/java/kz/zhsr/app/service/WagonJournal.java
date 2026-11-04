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
