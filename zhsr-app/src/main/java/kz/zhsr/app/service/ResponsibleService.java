package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.wagon.ResponsibleCandidate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ответственный за вагон: кого можно назначить. Для рейса (текущего или закончившегося) - бригада рейса и сотрудники
 * депо станции (станция прибытия, станция отцепки или прицепки); для передачи - сотрудники депо станции акта и
 * бригады рейсов поезда вагонов: последнего закончившегося, текущего и ближайшего. Сотрудник депо станции - текущая
 * запись usr_depot на депо этой станции. Роль не важна.
 */
@Service
public class ResponsibleService {

    private final Db db;
    private final CurrentUserService currentUserService;
    private final RefResolver refResolver;

    public ResponsibleService(Db db, CurrentUserService currentUserService, RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.refResolver = refResolver;
    }

    /**
     * Кого можно назначить ответственным (GET /wagons/responsibles): сначала бригада, затем сотрудники депо; по ФИО.
     *
     * @param station станция: сотрудники её депо; null - у рейса станция прибытия, иначе без них
     * @param trip    рейс: его бригада; null - без неё
     * @param wagons  номера вагонов: бригады рейсов поезда вагонов; пусто - без них
     */
    public List<ResponsibleCandidate> candidates(Long station, Long trip, List<String> wagons) throws Exception {
        currentUserService.get();
        if (station == null && trip != null) {
            DbRec arrival = db.loadSqlRec(WagonSql.tripArrivalStation(), Map.of("trip", trip));
            station = arrival == null ? null : arrival.getLong("station");
        }
        Set<Long> trips = new LinkedHashSet<>();
        if (trip != null) {
            trips.add(trip);
        }
        trips.addAll(trainTrips(wagons));

        Map<Long, ResponsibleCandidate> res = new LinkedHashMap<>();
        if (!trips.isEmpty()) {
            for (DbRec r : db.loadSql(WagonSql.crewCandidates(), Map.of("trips", List.copyOf(trips)))) {
                ResponsibleCandidate c = res.computeIfAbsent(r.getLong("usr"), ResponsibleCandidate::candidate);
                if (c.getTrip() == null) {
                    c.setTrip(new TripRef(r.getLong("trip")));
                    c.setCrewDutyType(r.getLong("crew_duty_type"));
                }
            }
        }
        if (station != null) {
            for (DbRec r : db.loadSql(WagonSql.depotStaff(), Map.of("station", station))) {
                res.computeIfAbsent(r.getLong("id"), ResponsibleCandidate::candidate).setDepotStaff(true);
            }
        }
        List<ResponsibleCandidate> list = new ArrayList<>(res.values());
        refResolver.resolve(list);
        refResolver.resolve(list.stream().map(ResponsibleCandidate::getTrip).filter(Objects::nonNull).toList());
        list.sort(Comparator.comparing((ResponsibleCandidate c) -> c.getTrip() == null)
                .thenComparing(c -> c.getName() == null ? "" : c.getName()));
        return list;
    }

    /**
     * Проверить выбранного ответственного: он среди тех, кого можно назначить.
     *
     * @param usr ответственный; null - не назначен, проверять нечего
     * @throws ApiException 400 - сотрудника нельзя назначить
     */
    public void check(Long usr, Long station, Long trip) throws Exception {
        check(usr, station, trip, List.of());
    }

    /**
     * Проверить выбранного ответственного за вагоны передачи: он среди тех, кого можно назначить.
     *
     * @param usr    ответственный; null - не назначен, проверять нечего
     * @param wagons номера вагонов
     * @throws ApiException 400 - сотрудника нельзя назначить
     */
    public void check(Long usr, Long station, Long trip, List<String> wagons) throws Exception {
        if (usr == null) {
            return;
        }
        if (candidates(station, trip, wagons).stream().noneMatch(u -> u.getId() == usr)) {
            throw ApiException.badRequest("Этого сотрудника нельзя назначить ответственным за вагон");
        }
    }

    /**
     * Рейсы поездов вагонов: у каждого поезда - последний закончившийся, выполняющийся и ближайший запланированный.
     */
    private List<Long> trainTrips(List<String> numbers) throws Exception {
        if (numbers == null || numbers.isEmpty()) {
            return List.of();
        }
        List<Long> res = new ArrayList<>();
        for (DbRec train : db.loadSql(WagonSql.currentTrainsOf(), Map.of("numbers", numbers))) {
            List<DbRec> trips = db.loadList("trip", Map.of("train", train.getLong("train"), "trip_status_type",
                    List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS, TripStatus.DONE)));
            trips.stream().filter(t -> t.getLong("trip_status_type") == TripStatus.IN_PROGRESS)
                    .forEach(t -> res.add(t.getLong("id")));
            trips.stream().filter(t -> t.getLong("trip_status_type") == TripStatus.DONE && t.get("arrived_dt") != null)
                    .max(Comparator.comparing(t -> t.getDateTimeTZ("arrived_dt")))
                    .ifPresent(t -> res.add(t.getLong("id")));
            trips.stream().filter(t -> t.getLong("trip_status_type") == TripStatus.PLANNED)
                    .min(Comparator.comparing(t -> t.getDateTimeTZ("planned_dt")))
                    .ifPresent(t -> res.add(t.getLong("id")));
        }
        return res;
    }

}
