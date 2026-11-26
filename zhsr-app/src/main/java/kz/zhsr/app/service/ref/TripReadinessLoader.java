package kz.zhsr.app.service.ref;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtCnv;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.ref.RefLoader;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.constants.ConductorCategory;
import kz.zhsr.app.constants.CrewDuty;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.TrainWagonChangeType;
import kz.zhsr.app.constants.TrainWagonKind;
import kz.zhsr.app.constants.TrainWagonPlanStatus;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.train.WagonProblem;
import kz.zhsr.app.dto.trip.TripCrew;
import kz.zhsr.app.dto.trip.TripReadiness;
import kz.zhsr.app.service.RouteService;
import kz.zhsr.app.service.TripLayerSql;
import kz.zhsr.app.service.TripNorm;
import kz.zhsr.app.service.TripService;
import kz.zhsr.app.service.WagonProblems;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Готовность рейсов к отправлению (Ф-РЕЙ-02): поезд назначен и ожидающие планы до отправления отмечены, вагоны на
 * станции отправления, ЛНП и ПЭМ назначены, норма проводников выполнена, недостачи экипировки нет, опись проведена. У рейса в статусе «Выполняется» - те же
 * пункты по текущему состоянию: состав - текущий по факту, вагоны должны быть в рейсе, планов до отправления нет.
 * Выполненное до отправления - прицепки и отцепки фактом (и отмеченные планы, и факты без плана): вне рейса после
 * планового отправления предыдущего рейса поезда и самого рейса - не позже отправления.
 * <p>
 * Данные блока рейсов загружаются списками, без запросов на каждый рейс: рейсы, станции маршрутов, последний факт
 * поездов, состав поездов, места вагонов, ожидающие планы поездов, бригады и ответственные на рейс. Состав на
 * отправлении - записи слоя «план», действующие на момент отправления: плановое отправление, но не раньше
 * последнего факта поезда (как в TrainService.tripWagons); у выполняющегося рейса - открытые записи слоя «факт».
 */
@Component
class TripReadinessLoader extends RefLoader<TripReadiness> {

    private final Db db;
    private final RouteService routeService;
    private final WagonProblems wagonProblems;

    TripReadinessLoader(Db db, RouteService routeService, WagonProblems wagonProblems) {
        this.db = db;
        this.routeService = routeService;
        this.wagonProblems = wagonProblems;
    }

    @Override
    public Class<TripReadiness> refClass() {
        return TripReadiness.class;
    }

    @Override
    protected List<DbRec> loadBlock(List<Long> ids) throws Exception {
        List<DbRec> trips = db.loadList("trip", Map.of("id", ids));
        List<Long> trains = trips.stream().map(t -> t.getLong("train")).filter(Objects::nonNull).distinct()
                .toList();
        Map<Long, List<DbRec>> stations = routeService.stations(UtDb.<Long>uniqueValues(trips, "route"));

        // момент состава на отправлении: плановое отправление, но не раньше последнего факта поезда
        Map<Long, OffsetDateTime> lastFact = new HashMap<>();
        if (!trains.isEmpty()) {
            for (DbRec r : db.loadSql(lastFacts(), Map.of("trains", trains, "fact", TrainWagonKind.FACT))) {
                lastFact.merge(r.getLong("train"), r.getDateTimeTZ("dt"), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        Map<Long, OffsetDateTime> moments = new HashMap<>();
        List<Long> tripTrains = new ArrayList<>();
        for (DbRec t : trips) {
            Long train = t.getLong("train");
            if (train == null) {
                continue;
            }
            if (inTrip(t)) {
                tripTrains.add(train);
                continue;
            }
            OffsetDateTime planned = t.getDateTimeTZ("planned_dt");
            OffsetDateTime last = lastFact.get(train);
            moments.put(t.getLong("id"), last != null && last.isAfter(planned) ? last : planned);
        }

        Map<Long, List<DbRec>> states = Map.of();
        Map<Long, List<DbRec>> waiting = Map.of();
        Map<Long, List<DbRec>> facts = Map.of();
        if (!tripTrains.isEmpty()) {
            facts = UtDb.collectGroupBy_records(db.loadSql(openStates(), Map.of("trains", tripTrains,
                    "kind", TrainWagonKind.FACT)), "train");
        }
        if (!moments.isEmpty()) {
            OffsetDateTime from = moments.values().stream().min(Comparator.naturalOrder()).orElseThrow();
            OffsetDateTime to = moments.values().stream().max(Comparator.naturalOrder()).orElseThrow();
            states = UtDb.collectGroupBy_records(db.loadSql(planStates(), Map.of("trains", trains,
                    "kind", TrainWagonKind.PLAN, "from", from, "to", to)), "train");
            waiting = UtDb.collectGroupBy_records(db.loadSql(waitingPlans(), Map.of("trains", trains,
                    "kind", TrainWagonKind.PLAN, "waiting", TrainWagonPlanStatus.WAITING)), "train");
        }

        // едущие вагоны рейсов на отправлении (у выполняющегося - сейчас), по позиции
        Map<Long, List<DbRec>> riding = new HashMap<>();
        for (DbRec t : trips) {
            long id = t.getLong("id");
            if (inTrip(t) && t.get("train") != null) {
                riding.put(id, facts.getOrDefault(t.getLong("train"), List.of()).stream()
                        .sorted(Comparator.comparingInt(r -> r.getInt("seq")))
                        .toList());
                continue;
            }
            OffsetDateTime moment = moments.get(id);
            if (moment == null) {
                continue;
            }
            riding.put(id, states.getOrDefault(t.getLong("train"), List.of()).stream()
                    .filter(r -> !r.getDateTimeTZ("dbeg").isAfter(moment)
                            && (r.get("dend") == null || r.getDateTimeTZ("dend").isAfter(moment)))
                    .sorted(Comparator.comparingInt(r -> r.getInt("seq")))
                    .toList());
        }
        Map<Long, DbRec> wagons = new HashMap<>();
        List<Long> wagonIds = riding.values().stream().flatMap(List::stream).map(r -> r.getLong("wagon")).distinct()
                .toList();
        if (!wagonIds.isEmpty()) {
            for (DbRec w : db.loadSql(wagonPlaces(), Map.of("wagons", wagonIds))) {
                wagons.put(w.getLong("wagon"), w);
            }
        }

        // недостача по последним описям едущих вагонов: вагон -> позиции шаблонов
        Map<Long, Set<Long>> shortages = new HashMap<>();
        if (!wagonIds.isEmpty()) {
            for (DbRec r : db.loadSql(equipShortages(), Map.of("wagons", wagonIds))) {
                shortages.computeIfAbsent(r.getLong("wagon"), k -> new HashSet<>())
                        .add(r.getLong("equip_list_template_item"));
            }
        }

        // вагоны, у которых описей не было вовсе
        Set<Long> listed = new HashSet<>();
        if (!wagonIds.isEmpty()) {
            for (DbRec r : db.loadSql(listedWagons(), Map.of("wagons", wagonIds))) {
                listed.add(r.getLong("wagon"));
            }
        }

        // факты поездов и рейсы поездов: выполненное до отправления рейса - факты вне рейса после планового
        // отправления предыдущего рейса поезда и факты самого рейса до его отправления
        Map<Long, List<DbRec>> trainFacts = Map.of();
        Map<Long, List<DbRec>> trainTrips = Map.of();
        if (!trains.isEmpty()) {
            trainFacts = UtDb.collectGroupBy_records(db.loadSql(trainFacts(), Map.of("trains", trains,
                    "fact", TrainWagonKind.FACT)), "train");
            trainTrips = UtDb.collectGroupBy_records(db.loadSql(trainTrips(), Map.of("trains", trains,
                    "cancelled", TripStatus.CANCELLED)), "train");
        }
        Map<Long, List<DbRec>> crew = UtDb.collectGroupBy_records(db.loadSql(crew(),
                Map.of("trips", ids, "senior", ConductorCategory.SENIOR)), "trip");
        Map<String, Object> params = TripLayerSql.params();
        params.put("trips", ids);
        Map<Long, List<DbRec>> responsibles = UtDb.collectGroupBy_records(db.loadSql(crewWagons(), params), "id");

        Map<Long, List<WagonProblem>> problems = problems(trips, stations, moments, riding, trains);

        List<DbRec> res = new ArrayList<>();
        for (DbRec t : trips) {
            long id = t.getLong("id");
            List<DbRec> points = stations.getOrDefault(t.getLong("route"), List.of()).stream()
                    .filter(rs -> rs.getBoolean("have_stop")).toList();
            DbRec departure = points.isEmpty() ? null : points.get(0);

            List<TripCrew.Wagon> composition = new ArrayList<>();
            for (DbRec r : riding.getOrDefault(id, List.of())) {
                DbRec w = wagons.get(r.getLong("wagon"));
                TripCrew.Wagon wagon = new TripCrew.Wagon();
                wagon.setWagon(r.getLong("wagon"));
                wagon.setNumber(w.getString("number"));
                wagon.setLocationType(w.getLong("location_type"));
                wagon.setPlace(w.getLong("place"));
                wagon.setPassenger(!TripService.NOT_PASSENGER.contains(w.getString("code")));
                wagon.setSeq(r.getInt("seq"));
                wagon.setRiding(true);
                composition.add(wagon);
            }
            Set<Long> ridingIds = composition.stream().map(TripCrew.Wagon::getWagon).collect(Collectors.toSet());

            // вагоны сотрудников - ответственные на рейс у вагонов, которые едут в поезде
            Map<Long, List<String>> usrWagons = new HashMap<>();
            for (DbRec r : responsibles.getOrDefault(id, List.of())) {
                if (ridingIds.contains(r.getLong("wagon"))) {
                    usrWagons.computeIfAbsent(r.getLong("usr"), k -> new ArrayList<>()).add(r.getString("number"));
                }
            }
            Map<Long, TripCrew.Member> members = new LinkedHashMap<>();
            for (DbRec r : crew.getOrDefault(id, List.of())) {
                TripCrew.Member m = new TripCrew.Member();
                m.setUsr(new UserRef(r.getLong("usr")));
                m.setCrewDutyType(r.getLong("crew_duty_type"));
                m.setSenior(r.getBoolean("senior"));
                m.setWagons(usrWagons.getOrDefault(r.getLong("usr"), List.of()));
                members.putIfAbsent(r.getLong("usr"), m);
            }

            Long train = t.getLong("train");
            List<String> pending = new ArrayList<>();
            if (train != null && departure != null && !inTrip(t)) {
                OffsetDateTime planned = t.getDateTimeTZ("planned_dt");
                for (DbRec c : waiting.getOrDefault(train, List.of())) {
                    Long changeTrip = c.getLong("trip");
                    boolean atDeparture = changeTrip != null && changeTrip == id
                            && c.getLong("route_station").longValue() == departure.getLong("id");
                    boolean before = changeTrip == null && !c.getDateTimeTZ("dt").isAfter(planned);
                    if ((atDeparture || before) && !pending.contains(c.getString("number"))) {
                        pending.add(c.getString("number"));
                    }
                }
            }

            // где должны быть вагоны: до отправления - на станции отправления, в пути - в этом рейсе
            long placeType = inTrip(t) ? LocationType.TRIP : LocationType.STATION;
            Long place = inTrip(t) ? Long.valueOf(id) : departure == null ? null : departure.getLong("station");
            DbRec rec = new DbRec();
            rec.put("id", id);
            rec.put("train", train != null);
            rec.put("pending", pending);
            // выполнено до отправления: факты вне рейса после планового отправления предыдущего рейса поезда и
            // факты самого рейса - не позже отправления (у выполняющегося - фактического, иначе планового)
            List<String> done = new ArrayList<>();
            if (train != null) {
                OffsetDateTime planned = t.getDateTimeTZ("planned_dt");
                OffsetDateTime until = inTrip(t) && t.get("departed_dt") != null ? t.getDateTimeTZ("departed_dt")
                        : planned;
                OffsetDateTime prev = trainTrips.getOrDefault(train, List.of()).stream()
                        .map(r -> r.getDateTimeTZ("planned_dt"))
                        .filter(dt -> dt.isBefore(planned))
                        .max(Comparator.naturalOrder()).orElse(null);
                for (DbRec c : trainFacts.getOrDefault(train, List.of())) {
                    Long changeTrip = c.getLong("trip");
                    OffsetDateTime dt = c.getDateTimeTZ("dt");
                    boolean outside = changeTrip == null && (prev == null || dt.isAfter(prev));
                    boolean own = changeTrip != null && changeTrip == id;
                    if ((outside || own) && !dt.isAfter(until) && !done.contains(c.getString("number"))) {
                        done.add(c.getString("number"));
                    }
                }
            }
            rec.put("done", done);
            rec.put("riding", composition.size());
            rec.put("problems", problems.getOrDefault(id, List.of()));
            rec.put("away", composition.stream()
                    .filter(w -> !Objects.equals(w.getLocationType(), placeType) || place == null || !place.equals(w.getPlace()))
                    .map(TripCrew.Wagon::getNumber)
                    .toList());
            rec.put("lnp", members.values().stream().anyMatch(m -> m.getCrewDutyType() == CrewDuty.LNP));
            rec.put("pem", members.values().stream().anyMatch(m -> m.getCrewDutyType() == CrewDuty.PEM));
            // позиции с недостачей: по каждому шаблону - в последней описи каждого едущего вагона
            Set<Long> shortItems = new HashSet<>();
            for (Long wagon : ridingIds) {
                shortItems.addAll(shortages.getOrDefault(wagon, Set.of()));
            }
            rec.put("equip_shortage", shortItems.size());
            rec.put("equip_no_list", composition.stream().filter(w -> !listed.contains(w.getWagon()))
                    .map(TripCrew.Wagon::getNumber).toList());
            rec.put("norm_not_ok", TripNorm.notOk(composition, new ArrayList<>(members.values())));
            res.add(rec);
        }
        return res;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void fill(TripReadiness ref, DbRec rec) {
        ref.setTrain(rec.getBoolean("train"));
        ref.setPending((List<String>) rec.get("pending"));
        ref.setDone((List<String>) rec.get("done"));
        ref.setRiding(rec.getInt("riding"));
        ref.setAway((List<String>) rec.get("away"));
        ref.setProblems((List<WagonProblem>) rec.get("problems"));
        ref.setLnp(rec.getBoolean("lnp"));
        ref.setPem(rec.getBoolean("pem"));
        ref.setEquipNoList((List<String>) rec.get("equip_no_list"));
        ref.setEquipShortage(rec.getInt("equip_shortage"));
        ref.setNormNotOk((List<String>) rec.get("norm_not_ok"));
    }

    /**
     * Неготовность вагонов рейсов. Вагон нужен от отправления (у выполняющегося рейса - с этого момента) до прибытия по
     * расписанию: едущий на отправлении - на станции отправления (в пути расположение не проверяется), прицепляемый
     * планом на остановке - с планового времени прицепки на станции остановки.
     */
    private Map<Long, List<WagonProblem>> problems(List<DbRec> trips, Map<Long, List<DbRec>> stations,
                                                   Map<Long, OffsetDateTime> moments,
                                                   Map<Long, List<DbRec>> riding, List<Long> trains)
            throws Exception {
        Map<Long, List<DbRec>> attaches = trains.isEmpty() ? Map.of() : UtDb.collectGroupBy_records(
                db.loadList("train_wagon_change", Map.of("train", trains, "kind", TrainWagonKind.PLAN,
                        "plan_status", TrainWagonPlanStatus.WAITING, "change_type", TrainWagonChangeType.ATTACH)),
                "train");
        OffsetDateTime now = OffsetDateTime.now();
        List<WagonProblems.Need> needs = new ArrayList<>();
        List<Long> needTrips = new ArrayList<>();
        for (DbRec t : trips) {
            long id = t.getLong("id");
            Long train = t.getLong("train");
            List<DbRec> route = stations.getOrDefault(t.getLong("route"), List.of());
            List<DbRec> points = route.stream().filter(rs -> rs.getBoolean("have_stop")).toList();
            if (train == null || points.isEmpty()) {
                continue;
            }
            boolean inTrip = inTrip(t);
            OffsetDateTime from = inTrip ? now : moments.get(id);
            DbRec first = points.get(0);
            DbRec last = points.get(points.size() - 1);
            int minutes = (int) (UtCnv.toLong(last.get("arrival_min")) - UtCnv.toLong(first.get("departure_min")));
            OffsetDateTime arrival = t.getDateTimeTZ("planned_dt").plusMinutes(minutes);
            OffsetDateTime to = arrival.isAfter(from) ? arrival : from.plusMinutes(1);
            Long departure = inTrip ? null : first.getLong("station");
            for (DbRec r : riding.getOrDefault(id, List.of())) {
                needs.add(new WagonProblems.Need(r.getLong("wagon"), train, departure, from, to));
                needTrips.add(id);
            }
            for (DbRec c : attaches.getOrDefault(train, List.of())) {
                Long changeTrip = c.getLong("trip");
                if (changeTrip == null || changeTrip != id
                        || (!inTrip && c.getLong("route_station").longValue() == first.getLong("id"))) {
                    continue;
                }
                OffsetDateTime dt = c.getDateTimeTZ("dt");
                needs.add(new WagonProblems.Need(c.getLong("wagon"), train, c.getLong("station"), dt,
                        to.isAfter(dt) ? to : dt.plusMinutes(1)));
                needTrips.add(id);
            }
        }
        Map<Long, List<WagonProblem>> res = new HashMap<>();
        List<List<WagonProblem>> found = wagonProblems.find(needs);
        for (int i = 0; i < needs.size(); i++) {
            if (!found.get(i).isEmpty()) {
                res.computeIfAbsent(needTrips.get(i), k -> new ArrayList<>()).addAll(found.get(i));
            }
        }
        return res;
    }

    private static boolean inTrip(DbRec trip) {
        return trip.getLong("trip_status_type") == TripStatus.IN_PROGRESS;
    }

    /** Последний Факт журнала поездов: до двух строк на поезд. Параметры trains, fact. */
    private static String lastFacts() {
        return """
                select train_wagon_change.train, max(train_wagon_change.dt) as dt
                from train_wagon_change
                where train_wagon_change.train = any(:trains) and train_wagon_change.kind = :fact
                group by train_wagon_change.train
                union all
                select train_wagon_seq.train, max(train_wagon_seq.dt) as dt
                from train_wagon_seq
                where train_wagon_seq.train = any(:trains) and train_wagon_seq.kind = :fact
                group by train_wagon_seq.train
                """;
    }

    /** Записи участия поездов слоя kind, действующие в какой-то момент промежутка [from, to]. */
    private static String planStates() {
        return """
                select train_wagon.train, train_wagon.wagon, train_wagon.seq, train_wagon.dbeg, train_wagon.dend
                from train_wagon
                where train_wagon.train = any(:trains) and train_wagon.kind = :kind and train_wagon.dbeg <= :to
                  and (train_wagon.dend is null or train_wagon.dend > :from)
                """;
    }

    /** Открытые записи участия поездов слоя kind - текущий состав. Параметры trains, kind. */
    private static String openStates() {
        return """
                select train_wagon.train, train_wagon.wagon, train_wagon.seq
                from train_wagon
                where train_wagon.train = any(:trains) and train_wagon.kind = :kind and train_wagon.dend is null
                """;
    }

    /** Место вагонов: вид места и id станции, депо, парка или рейса; тип вагона - для признака «пассажирский». */
    private static String wagonPlaces() {
        return """
                select wagon_info.wagon, wagon.number, wagon_type.code, wagon_info.location_type,
                       coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard, wagon_info.trip) as place
                from wagon_info
                join wagon on wagon.id = wagon_info.wagon
                join wagon_type on wagon_type.id = wagon.wagon_type
                where wagon_info.wagon = any(:wagons)
                """;
    }

    /**
     * Позиции с недостачей (фактическое количество меньше требуемого) в последней описи вагона по каждому шаблону:
     * вагон и позиция. Параметр wagons.
     */
    private static String equipShortages() {
        return """
                select equip_list.wagon, equip_list_value.equip_list_template_item
                from equip_list_value
                join equip_list on equip_list.id = equip_list_value.equip_list
                where equip_list.wagon = any(:wagons)
                  and equip_list_value.quantity_required is not null
                  and equip_list_value.quantity_actual < equip_list_value.quantity_required
                  and equip_list.id = (select last.id from equip_list as last
                                       where last.wagon = equip_list.wagon
                                         and last.equip_list_template = equip_list.equip_list_template
                                       order by last.completed_dt desc, last.id desc
                                       limit 1)
                """;
    }

    /** Вагоны, у которых есть хотя бы одна опись экипировки. Параметр wagons. */
    private static String listedWagons() {
        return """
                select distinct equip_list.wagon
                from equip_list
                where equip_list.wagon = any(:wagons)
                """;
    }

    /** Ожидающие планы поездов с номером вагона; у смены порядка - все её вагоны. Параметры trains, kind, waiting. */
    private static String waitingPlans() {
        return """
                select train_wagon_change.train, train_wagon_change.trip, train_wagon_change.route_station,
                       train_wagon_change.dt, wagon.number
                from train_wagon_change
                join wagon on wagon.id = train_wagon_change.wagon
                where train_wagon_change.train = any(:trains) and train_wagon_change.kind = :kind
                  and train_wagon_change.plan_status = :waiting
                union all
                select train_wagon_seq.train, train_wagon_seq.trip, train_wagon_seq.route_station,
                       train_wagon_seq.dt, wagon.number
                from train_wagon_seq
                join train_wagon_seq_item on train_wagon_seq_item.train_wagon_seq = train_wagon_seq.id
                join wagon on wagon.id = train_wagon_seq_item.wagon
                where train_wagon_seq.train = any(:trains) and train_wagon_seq.kind = :kind
                  and train_wagon_seq.plan_status = :waiting
                order by dt, number
                """;
    }

    /** Факты журнала поездов с номером вагона и рейсом; у смены порядка - все её вагоны. Параметры trains, fact. */
    private static String trainFacts() {
        return """
                select train_wagon_change.train, train_wagon_change.trip, train_wagon_change.dt, wagon.number
                from train_wagon_change
                join wagon on wagon.id = train_wagon_change.wagon
                where train_wagon_change.train = any(:trains) and train_wagon_change.kind = :fact
                union all
                select train_wagon_seq.train, train_wagon_seq.trip, train_wagon_seq.dt, wagon.number
                from train_wagon_seq
                join train_wagon_seq_item on train_wagon_seq_item.train_wagon_seq = train_wagon_seq.id
                join wagon on wagon.id = train_wagon_seq_item.wagon
                where train_wagon_seq.train = any(:trains) and train_wagon_seq.kind = :fact
                order by dt, number
                """;
    }

    /** Неотменённые рейсы поездов: плановое отправление. Параметры trains, cancelled. */
    private static String trainTrips() {
        return """
                select trip.train, trip.planned_dt
                from trip
                where trip.train = any(:trains) and trip.trip_status_type <> :cancelled
                """;
    }

    /** Действующая бригада рейсов: должность и старший ли проводник. Параметры trips, senior. */
    private static String crew() {
        return """
                select trip_crew.trip, trip_crew.usr, trip_crew.crew_duty_type,
                       exists (select 1 from usr_category
                               where usr_category.usr = usr.id and usr_category.dend is null
                                 and usr_category.category_type = :senior) as senior
                from trip_crew
                join usr on usr.id = trip_crew.usr
                where trip_crew.trip = any(:trips) and trip_crew.dend is null
                order by usr.full_name, usr.login
                """;
    }

    /**
     * Ответственные на рейс по рейсам: id рейса, вагон, номер, сотрудник. Параметры trips и TripLayerSql.params.
     */
    private static String crewWagons() {
        return """
                select trip.id, wagon_responsible.wagon, wagon.number, wagon_responsible_usr.usr
                from wagon_responsible
                join wagon_responsible_usr on wagon_responsible_usr.wagon_responsible = wagon_responsible.id
                join wagon on wagon.id = wagon_responsible.wagon
                join usr on usr.id = wagon_responsible_usr.usr
                join trip on trip.id = any(:trips)
                where %s
                order by wagon.number, usr.full_name, usr.login
                """.formatted(TripLayerSql.WHERE);
    }

}
