package kz.zhsr.app.service;

import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.RoleType;
import kz.zhsr.app.constants.WagonStatus;
import kz.zhsr.app.dto.wagon.WagonRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Ввод вагона в эксплуатацию (Ф-ВАГ-01): паспорт, депо приписки, пробег на момент ввода, начальное расположение и
 * состояние. Вводит администратор.
 */
@Service
public class WagonEntryService {

    /**
     * Новый вагон. Поля - по колонкам wagon; place - место начального расположения.
     *
     * @param number          номер: 5-8 цифр, уникален
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

    public WagonEntryService(Db db, CurrentUserService currentUserService, ListService listService) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.listService = listService;
    }

    /**
     * Ввести вагон (POST /wagons): вагон, первые записи журналов расположения и состояния, запись журнала
     * ответственного «не назначен», кэш wagon_info. Записи журналов начинаются временем ввода в систему.
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
        String number = body.number() == null ? "" : body.number().trim();
        if (!number.matches("\\d{5,8}")) {
            throw ApiException.badRequest("Номер вагона - от 5 до 8 цифр");
        }
        if (body.wagonType() == null || body.modelType() == null || body.depot() == null || body.dbeg() == null
                || body.locationType() == null || body.place() == null || body.wagonStatusType() == null) {
            throw ApiException.badRequest("Заполните все поля");
        }
        if (body.wagonStatusType() == WagonStatus.IN_TRIP) {
            throw ApiException.badRequest("Состояние «в пути» вагон получает только в рейсе");
        }
        int initialRunKm = body.initialRunKm() == null ? 0 : body.initialRunKm();
        if (initialRunKm < 0) {
            throw ApiException.badRequest("Пробег - целое неотрицательное число");
        }
        String placeColumn = placeColumn(body.locationType());
        if (db.loadRec(placeColumn, body.place()) == null) {
            throw ApiException.badRequest("Место начального расположения не найдено");
        }
        if (db.loadRec("wagon", Map.of("number", number)) != null) {
            throw ApiException.conflict("Вагон № " + number + " уже есть");
        }

        OffsetDateTime now = OffsetDateTime.now();
        try {
            Map<String, Object> wagon = new HashMap<>();
            wagon.put("number", number);
            wagon.put("wagon_type", body.wagonType());
            wagon.put("model_type", body.modelType());
            wagon.put("build_year", body.buildYear());
            wagon.put("maker", body.maker() == null || body.maker().isBlank() ? null : body.maker().trim());
            wagon.put("dbeg", body.dbeg());
            wagon.put("depot", body.depot());
            wagon.put("initial_run_km", initialRunKm);
            long id = db.insertRec("wagon", wagon);

            db.insertRec("wagon_location", Map.of("wagon", id, "dbeg", now, "location_type", body.locationType(),
                    placeColumn, body.place()));
            db.insertRec("wagon_status", Map.of("wagon", id, "dbeg", now,
                    "wagon_status_type", body.wagonStatusType()));
            db.insertRec("wagon_responsible", Map.of("wagon", id, "dbeg", now));

            Map<String, Object> info = new HashMap<>();
            info.put("wagon", id);
            info.put("dbeg_location", now);
            info.put("location_type", body.locationType());
            info.put(placeColumn, body.place());
            info.put("dbeg_status", now);
            info.put("wagon_status_type", body.wagonStatusType());
            info.put("dbeg_responsible", now);
            info.put("run_km", initialRunKm);
            db.insertRec("wagon_info", info);
        } catch (Exception e) {
            throw DbErrors.translate(e);
        }
        return listService.wagonByNumber(number);
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
