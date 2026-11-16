package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.ActStatus;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.WagonStatus;
import kz.zhsr.app.dto.wagon.WagonRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ручная правка вагона администратором: смена состояния (Ф-ВАГ-02) и расположения (Ф-ВАГ-03) с обязательным
 * комментарием. Правка - документ wagon_edit: ссылается на открытые ею записи журналов.
 */
@Service
public class WagonEditService {

    /**
     * Смена состояния.
     *
     * @param wagonStatusType новое состояние, кроме «в пути»
     * @param comment         комментарий
     */
    public record StatusBody(Long wagonStatusType, String comment) {
    }

    /**
     * Смена расположения.
     *
     * @param locationType   вид нового расположения: станция, депо или парк отстоя
     * @param place          место: station, depot или yard - по locationType
     * @param responsibleUsr ответственный за вагон: сотрудник депо станции нового расположения; null - не назначен
     * @param comment        комментарий
     */
    public record LocationBody(Long locationType, Long place, Long responsibleUsr, String comment) {
    }

    /**
     * Смена ответственного.
     *
     * @param responsibleUsr ответственный за вагон: сотрудник депо станции вагона или член бригады последнего и
     *                       ближайшего рейса поезда вагона; null - не назначен
     */
    public record ResponsibleBody(Long responsibleUsr) {
    }

    private final Db db;
    private final CurrentUserService currentUserService;
    private final WagonJournal wagonJournal;
    private final ResponsibleService responsibleService;
    private final ListService listService;

    public WagonEditService(Db db, CurrentUserService currentUserService, WagonJournal wagonJournal,
                            ResponsibleService responsibleService, ListService listService) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.wagonJournal = wagonJournal;
        this.responsibleService = responsibleService;
        this.listService = listService;
    }

    /**
     * Сменить состояние вагона (POST /wagons/{id}/status); расположение не меняется.
     *
     * @throws ApiException 403 - не администратор; 400 - нет комментария или неверное состояние; 409 - вагон в рейсе
     */
    @Transactional
    public WagonRow changeStatus(long id, StatusBody body) throws Exception {
        CurrentUser me = requireAdmin();
        DbRec wagon = requireWagon(id);
        String comment = requireComment(body.comment());
        if (body.wagonStatusType() == null || body.wagonStatusType() == WagonStatus.IN_TRIP) {
            throw ApiException.badRequest("Выберите состояние; «в пути» вагон получает только в рейсе");
        }
        requireNotInTrip(id);
        OffsetDateTime now = OffsetDateTime.now();
        long opened = wagonJournal.moveStatus(id, now, body.wagonStatusType());
        Map<String, Object> edit = edit(id, me, now, comment);
        edit.put("wagon_status", opened);
        db.insertRec("wagon_edit", edit);
        return listService.wagonByNumber(wagon.getString("number"));
    }

    /**
     * Сменить расположение вагона (POST /wagons/{id}/location): станция, депо или парк отстоя; состояние не меняется.
     * Ответственный - выбранный при смене.
     *
     * @throws ApiException 403 - не администратор; 400 - не заполнено или неверно; 409 - вагон в рейсе или ждёт приёма
     *                      по акту
     */
    @Transactional
    public WagonRow changeLocation(long id, LocationBody body) throws Exception {
        CurrentUser me = requireAdmin();
        DbRec wagon = requireWagon(id);
        String comment = requireComment(body.comment());
        if (body.locationType() == null || body.place() == null) {
            throw ApiException.badRequest("Выберите новое расположение");
        }
        String column = placeColumn(body.locationType());
        DbRec place = db.loadRec(column, body.place());
        if (place == null) {
            throw ApiException.badRequest("Место расположения не найдено");
        }
        requireNotInTrip(id);
        if (db.loadSqlRec(WagonSql.pendingTransfer(), Map.of("wagon", id, "pending", ActStatus.PENDING)) != null) {
            throw ApiException.conflict("Вагон ждёт приёма по акту передачи - сначала примите или отмените акт");
        }
        Long station = "station".equals(column) ? body.place() : place.getLong("station", null);
        responsibleService.check(body.responsibleUsr(), station, null);

        OffsetDateTime now = OffsetDateTime.now();
        Long stationId = "station".equals(column) ? body.place() : null;
        Long depot = "depot".equals(column) ? body.place() : null;
        Long yard = "yard".equals(column) ? body.place() : null;
        long location = wagonJournal.moveLocation(id, now, body.locationType(), stationId, depot, yard);
        long responsible = wagonJournal.assign(id, now, body.responsibleUsr());
        Map<String, Object> edit = edit(id, me, now, comment);
        edit.put("wagon_location", location);
        edit.put("wagon_responsible", responsible);
        db.insertRec("wagon_edit", edit);
        return listService.wagonByNumber(wagon.getString("number"));
    }

    /**
     * Сменить ответственного за вагон без акта (POST /wagons/{id}/responsible, Ф-ВАГ-07): назначить сотрудника или
     * снять назначение («не назначен»). Меняет мастер депо станции вагона или администратор; вагон не в рейсе и не ждёт
     * приёма по акту. Время - сейчас; запись ручной правки - без комментария.
     *
     * @throws ApiException 403 - не мастер депо станции вагона и не администратор; 409 - вагон в рейсе или ждёт приёма
     *                      по акту
     */
    @Transactional
    public WagonRow changeResponsible(long id, ResponsibleBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec wagon = requireWagon(id);
        requireNotInTrip(id);
        Long station = stationOf(id);
        boolean master = me.hasRole(RoleType.MASTER) && station != null && station.equals(me.depotStation());
        if (!me.hasRole(RoleType.ADMIN) && !master) {
            throw ApiException.forbidden("Ответственного за вагон меняет мастер депо станции вагона");
        }
        if (db.loadSqlRec(WagonSql.pendingTransfer(), Map.of("wagon", id, "pending", ActStatus.PENDING)) != null) {
            throw ApiException.conflict("Вагон ждёт приёма по акту передачи - ответственного выбирают в акте");
        }
        responsibleService.check(body.responsibleUsr(), station, null, List.of(wagon.getString("number")));

        OffsetDateTime now = OffsetDateTime.now();
        long responsible = wagonJournal.assign(id, now, body.responsibleUsr());
        Map<String, Object> edit = edit(id, me, now, null);
        edit.put("wagon_responsible", responsible);
        db.insertRec("wagon_edit", edit);
        return listService.wagonByNumber(wagon.getString("number"));
    }

    /** Станция вагона: сама станция, станция депо или парка отстоя; вагон в рейсе - null. */
    private Long stationOf(long wagon) throws Exception {
        DbRec info = db.loadRec("wagon_info", Map.of("wagon", wagon));
        if (info == null) {
            return null;
        }
        long type = info.getLong("location_type");
        if (type == LocationType.STATION) {
            return info.getLong("station", null);
        }
        if (type == LocationType.DEPOT) {
            return db.loadRec("depot", info.getLong("depot")).getLong("station", null);
        }
        if (type == LocationType.YARD) {
            return db.loadRec("yard", info.getLong("yard")).getLong("station", null);
        }
        return null;
    }

    private CurrentUser requireAdmin() {
        CurrentUser me = currentUserService.get();
        if (!me.hasRole(RoleType.ADMIN)) {
            throw ApiException.forbidden("Состояние и расположение вагона вручную меняет администратор");
        }
        return me;
    }

    private DbRec requireWagon(long id) throws Exception {
        DbRec wagon = db.loadRec("wagon", id);
        if (wagon == null) {
            throw ApiException.notFound("Вагон не найден");
        }
        return wagon;
    }

    private static String requireComment(String comment) {
        if (comment == null || comment.isBlank()) {
            throw ApiException.badRequest("Напишите комментарий");
        }
        return comment.trim();
    }

    /** Вагон в рейсе меняется только отметками рейса и актами отцепки и прицепки. */
    private void requireNotInTrip(long wagon) throws Exception {
        DbRec info = db.loadRec("wagon_info", Map.of("wagon", wagon));
        if (info != null && info.getLong("location_type") == LocationType.TRIP) {
            throw ApiException.conflict("Вагон в рейсе - меняется отметками рейса и актами отцепки и прицепки");
        }
    }

    private static Map<String, Object> edit(long wagon, CurrentUser me, OffsetDateTime now, String comment) {
        Map<String, Object> edit = new HashMap<>();
        edit.put("wagon", wagon);
        edit.put("created_by_usr", me.id());
        edit.put("created_dt", now);
        edit.put("comment", comment);
        return edit;
    }

    /** Колонка места по виду расположения: станция, депо или парк отстоя. */
    private static String placeColumn(long locationType) {
        if (locationType == LocationType.STATION) {
            return "station";
        }
        if (locationType == LocationType.DEPOT) {
            return "depot";
        }
        if (locationType == LocationType.YARD) {
            return "yard";
        }
        throw ApiException.badRequest("Новое расположение - станция, депо или парк отстоя");
    }

}
