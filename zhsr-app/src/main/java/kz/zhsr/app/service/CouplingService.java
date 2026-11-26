package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.kis.kisdbtools.repository.DbRecMapper;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.TrainWagonChangeType;
import kz.zhsr.app.dto.train.WagonBusy;
import kz.zhsr.app.dto.trip.CouplingWagon;
import kz.zhsr.app.dto.trip.TripCrew;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Прицепка и отцепка в рейсе сразу, без плана (Ф-АКТ-01): список вагонов и оформление. Права и проверки - в
 * {@link TrainService}: начальник поезда рейса, мастер депо станции маршрута, администратор; рейс в статусе
 * «выполняется», промежуточная остановка.
 */
@Service
public class CouplingService {

    /**
     * Прицепка или отцепка в рейсе.
     *
     * @param changeType     вид: константы TrainWagonChangeType
     * @param routeStation   промежуточная остановка маршрута (route_station)
     * @param comment        основания, примечания (приказ, причина); может быть пусто
     * @param wagons         id вагонов
     * @param time           фактическое время, ISO с часовым поясом; null - сейчас
     * @param seq            прицепка: позиция первого вагона в поезде, с 1; null - в конец
     * @param responsibleUsrs ответственные за вагоны; пусто - не назначены
     */
    public record CouplingBody(int changeType, long routeStation, String comment, List<Long> wagons,
                               OffsetDateTime time, Integer seq, List<Long> responsibleUsrs) {
    }

    private final Db db;
    private final TripService tripService;
    private final TrainService trainService;
    private final RefResolver refResolver;

    public CouplingService(Db db, TripService tripService, TrainService trainService, RefResolver refResolver) {
        this.db = db;
        this.tripService = tripService;
        this.trainService = trainService;
        this.refResolver = refResolver;
    }

    /**
     * Вагоны для прицепки или отцепки: для отцепки - которые едут в поезде рейса, по позиции; для прицепки - станции,
     * её депо и парка отстоя. Прицепить можно только вагон на самой станции, не занятый в другом поезде.
     *
     * @param station станция остановки; для отцепки не нужна
     */
    public List<CouplingWagon> wagons(long id, int type, Long station) throws Exception {
        tripService.requireTrip(id);
        if (type == TrainWagonChangeType.DETACH) {
            return tripService.load(id).getWagons().stream()
                    .filter(TripCrew.Wagon::isRiding)
                    .map(w -> {
                        CouplingWagon c = new CouplingWagon();
                        c.setWagon(w.getWagon());
                        c.setNumber(w.getNumber());
                        c.setSeq(w.getSeq());
                        c.setWagonType(w.getWagonType());
                        c.setLocationType(w.getLocationType());
                        c.setPlace(w.getPlace());
                        c.setWagonStatusType(w.getWagonStatusType());
                        c.setAvailable(true);
                        return c;
                    }).toList();
        }
        if (type != TrainWagonChangeType.ATTACH) {
            throw ApiException.badRequest("Неизвестный вид изменения");
        }
        if (station == null) {
            return List.of();
        }
        List<DbRec> recs = db.loadSql(TripSql.attachCandidates(), Map.of("station", station));
        List<CouplingWagon> res = DbRecMapper.toList(recs, CouplingWagon.class);
        Map<String, WagonBusy> busy = trainService.factBusy(res.stream().map(CouplingWagon::getWagon).toList())
                .stream().collect(Collectors.toMap(WagonBusy::getNumber, b -> b, (a, b) -> a));
        for (CouplingWagon w : res) {
            w.setBusy(busy.get(w.getNumber()));
            w.setAvailable(Objects.equals(w.getLocationType(), LocationType.STATION) && w.getBusy() == null);
        }
        refResolver.resolve(res);
        return res;
    }

    /**
     * Прицепить или отцепить вагоны в рейсе фактом. Отцепленный вагон встаёт на станцию остановки, «в простое», и
     * снимается с проводников; они остаются на рейсе. Прицепленный вагон уходит «в рейс», «в пути», и входит в поезд
     * рейса. Ответственные - выбранные; пусто - не назначены.
     */
    @Transactional
    public TripCrew couple(long id, CouplingBody body) throws Exception {
        DbRec trip = tripService.requireTrip(id);
        Long train = trip.getLong("train");
        if (train == null) {
            throw ApiException.conflict("Поезд рейса не назначен");
        }
        TrainService.ChangeBody change = new TrainService.ChangeBody(body.wagons(), true, body.time(), null,
                body.routeStation(), body.seq(), body.comment(), body.responsibleUsrs());
        if (body.changeType() == TrainWagonChangeType.ATTACH) {
            trainService.attach(train, change);
        } else if (body.changeType() == TrainWagonChangeType.DETACH) {
            trainService.detach(train, change);
        } else {
            throw ApiException.badRequest("Неизвестный вид изменения");
        }
        return tripService.load(id);
    }

}
