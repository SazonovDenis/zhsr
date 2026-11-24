package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtCnv;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ввод вагона в эксплуатацию (Ф-ВАГ-01): паспорт, депо приписки, пробег на момент ввода, начальное расположение и
 * состояние. Исправление данных вагона (Ф-ВАГ-08): те же поля. Вводит и исправляет администратор.
 */
@Service
public class WagonEntryService {

    /**
     * Данные вагона: ввод и исправление. Поля - по колонкам wagon; place - место начального расположения.
     *
     * @param number          номер: 5-20 знаков - цифры и дробная черта, уникален
     * @param wagonType       тип вагона (wagon_type)
     * @param modelType       модель (model_type)
     * @param buildYear       год постройки; null - не указан
     * @param maker           изготовитель; пусто - не указан
     * @param depot           депо приписки (depot)
     * @param dbeg            дата ввода в эксплуатацию
     * @param initialRunKm    пробег на момент ввода, км; null - 0
     * @param locationType    вид начального расположения: станция, депо или парк отстоя (location_type)
     * @param place           место начального расположения: station, depot или yard - по locationType
     * @param wagonStatusType начальное состояние (wagon_status_type), кроме «в пути»
     */
    public record WagonBody(String number, Long wagonType, Long modelType, Integer buildYear, String maker, Long depot,
                            LocalDate dbeg, Integer initialRunKm, Long locationType, Long place,
                            Long wagonStatusType) {
    }

    private final Db db;
    private final CurrentUserService currentUserService;
    private final ListService listService;
    private final WagonJournal wagonJournal;
    private final WagonRun wagonRun;

    public WagonEntryService(Db db, CurrentUserService currentUserService, ListService listService,
                             WagonJournal wagonJournal, WagonRun wagonRun) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.listService = listService;
        this.wagonJournal = wagonJournal;
        this.wagonRun = wagonRun;
    }

    /**
     * Кэш и журналы вагона: удаляются вместе с ним, от ссылающихся на другие к остальным; кэш wagon_info ссылается на
     * журнал ответственных - первым; состав ответственных (wagon_responsible_usr) - каскадом.
     */
    private static final List<String> WAGON_JOURNALS = List.of(
            "wagon_info", "wagon_edit", "wagon_responsible", "wagon_status", "wagon_location");

    /**
     * Удалить вагон (DELETE /wagons/{id}): администратор. Журналы вагона удаляются вместе с ним; на вагон ссылаются
     * поезда, заявки, чек-листы, акты... - удалить не даст внешний ключ (сообщение - FkMessages), всё откатывается.
     */
    @Transactional
    public void delete(long id) throws Exception {
        if (!currentUserService.get().hasRole(RoleType.ADMIN)) {
            throw ApiException.forbidden("Вагон удаляет администратор");
        }
        if (db.loadRec("wagon", id) == null) {
            throw ApiException.notFound("Вагон не найден");
        }
        try {
            for (String table : WAGON_JOURNALS) {
                db.execSql("delete from " + table + " where wagon = :id", Map.of("id", id));
            }
            db.deleteRec("wagon", id);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
    }

    /**
     * Ввести вагон (POST /wagons): вагон, первые записи журналов расположения и состояния, кэш wagon_info;
     * ответственных у нового вагона нет. Записи журналов начинаются временем ввода в систему.
     *
     * @return вагон как строка списка вагонов
     * @throws ApiException 403 - не администратор; 400 - не заполнено или неверно; 409 - номер уже есть
     */
    @Transactional
    public WagonRow create(WagonBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        if (!me.hasRole(RoleType.ADMIN)) {
            throw ApiException.forbidden("Вагон вводит администратор");
        }
        String number = number(body);
        if (body.wagonType() == null || body.modelType() == null || body.depot() == null || body.dbeg() == null
                || body.locationType() == null || body.place() == null || body.wagonStatusType() == null) {
            throw ApiException.badRequest("Заполните все поля");
        }
        if (body.wagonStatusType() == WagonStatus.IN_TRIP) {
            throw ApiException.badRequest("Состояние «в пути» вагон получает только в рейсе");
        }
        int initialRunKm = initialRunKm(body);
        String placeColumn = placeColumn(body.locationType());
        if (db.loadRec(placeColumn, body.place()) == null) {
            throw ApiException.badRequest("Место начального расположения не найдено");
        }
        if (db.loadRec("wagon", Map.of("number", number)) != null) {
            throw ApiException.conflict("Вагон № " + number + " уже есть");
        }

        OffsetDateTime now = OffsetDateTime.now();
        try {
            long id = db.insertRec("wagon", values(body, number, initialRunKm));

            db.insertRec("wagon_location", Map.of("wagon", id, "dbeg", now, "location_type", body.locationType(),
                    placeColumn, body.place()));
            db.insertRec("wagon_status", Map.of("wagon", id, "dbeg", now,
                    "wagon_status_type", body.wagonStatusType()));

            Map<String, Object> info = new HashMap<>();
            info.put("wagon", id);
            info.put("dbeg_location", now);
            info.put("location_type", body.locationType());
            info.put(placeColumn, body.place());
            info.put("dbeg_status", now);
            info.put("wagon_status_type", body.wagonStatusType());
            info.put("run_km", initialRunKm);
            db.insertRec("wagon_info", info);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        return listService.wagonByNumber(number);
    }

    /**
     * Исправить данные вагона (PUT /wagons/{id}): паспорт - без ограничений; изменённые расположение и состояние -
     * ручная правка wagon_edit без комментария: новые записи журналов текущим временем, ответственные не меняются.
     * Модель, депо приписки, расположение и состояние могут быть пусты: пустые расположение и состояние не меняются.
     *
     * @return вагон как строка списка вагонов
     * @throws ApiException 403 - не администратор; 400 - не заполнено или неверно; 409 - номер уже есть, вагон в рейсе
     *                      или ждёт приёма по акту
     */
    @Transactional
    public WagonRow update(long id, WagonBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        if (!me.hasRole(RoleType.ADMIN)) {
            throw ApiException.forbidden("Данные вагона исправляет администратор");
        }
        if (db.loadRec("wagon", id) == null) {
            throw ApiException.notFound("Вагон не найден");
        }
        String number = number(body);
        if (body.wagonType() == null || body.dbeg() == null) {
            throw ApiException.badRequest("Заполните все поля");
        }
        int initialRunKm = initialRunKm(body);
        DbRec same = db.loadRec("wagon", Map.of("number", number));
        if (same != null && same.getLong("id") != id) {
            throw ApiException.conflict("Вагон № " + number + " уже есть");
        }
        try {
            Map<String, Object> wagon = values(body, number, initialRunKm);
            wagon.put("id", id);
            db.updateRec("wagon", wagon);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        wagonRun.recalc(id);
        changeState(id, body, me);
        return listService.wagonByNumber(number);
    }

    /** Изменённые при исправлении расположение и состояние - в журналы, одной ручной правкой. */
    private void changeState(long id, WagonBody body, CurrentUser me) throws Exception {
        DbRec info = db.loadRec("wagon_info", Map.of("wagon", id));
        String column = body.locationType() == null || body.place() == null ? null : placeColumn(body.locationType());
        boolean newLocation = column != null && (!Objects.equals(info.getLong("location_type"),
                body.locationType()) || !Objects.equals(info.getLong(column), body.place()));
        boolean newStatus = body.wagonStatusType() != null
                && !Objects.equals(info.getLong("wagon_status_type"), body.wagonStatusType());
        if (!newLocation && !newStatus) {
            return;
        }
        if (UtCnv.toLong(info.get("location_type")) == LocationType.TRIP) {
            throw ApiException.conflict("Вагон в рейсе - меняется отметками рейса и актами отцепки и прицепки");
        }
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> edit = new HashMap<>();
        edit.put("wagon", id);
        edit.put("created_by_usr", me.id());
        edit.put("created_dt", now);
        if (newLocation) {
            if (db.loadRec(column, body.place()) == null) {
                throw ApiException.badRequest("Место расположения не найдено");
            }
            if (db.loadSqlRec(WagonSql.pendingTransfer(), Map.of("wagon", id, "pending", ActStatus.PENDING)) != null) {
                throw ApiException.conflict("Вагон ждёт приёма по акту передачи - сначала примите или отмените акт");
            }
            edit.put("wagon_location", wagonJournal.moveLocation(id, now, body.locationType(),
                    "station".equals(column) ? body.place() : null, "depot".equals(column) ? body.place() : null,
                    "yard".equals(column) ? body.place() : null));
        }
        if (newStatus) {
            if (body.wagonStatusType() == WagonStatus.IN_TRIP) {
                throw ApiException.badRequest("Состояние «в пути» вагон получает только в рейсе");
            }
            edit.put("wagon_status", wagonJournal.moveStatus(id, now, body.wagonStatusType()));
        }
        db.insertRec("wagon_edit", edit);
    }

    private static String number(WagonBody body) {
        String number = body.number() == null ? "" : body.number().trim();
        if (!number.matches("[\\d/]{5,20}")) {
            throw ApiException.badRequest("Номер вагона - от 5 до 20 знаков: цифры и дробная черта");
        }
        return number;
    }

    private static int initialRunKm(WagonBody body) {
        int initialRunKm = body.initialRunKm() == null ? 0 : body.initialRunKm();
        if (initialRunKm < 0) {
            throw ApiException.badRequest("Пробег - целое неотрицательное число");
        }
        return initialRunKm;
    }

    /** Поля вагона из тела запроса. */
    private static Map<String, Object> values(WagonBody body, String number, int initialRunKm) {
        Map<String, Object> wagon = new HashMap<>();
        wagon.put("number", number);
        wagon.put("wagon_type", body.wagonType());
        wagon.put("model_type", body.modelType());
        wagon.put("build_year", body.buildYear());
        wagon.put("maker", body.maker() == null || body.maker().isBlank() ? null : body.maker().trim());
        wagon.put("dbeg", body.dbeg());
        wagon.put("depot", body.depot());
        wagon.put("initial_run_km", initialRunKm);
        return wagon;
    }

    /** Колонка места по виду расположения: станция, депо или парк отстоя; «в рейсе» вагон не вводится. */
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
        throw ApiException.badRequest("Вагон вводится на станцию, в депо или в парк отстоя");
    }

}
