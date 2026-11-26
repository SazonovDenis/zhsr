package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtCnv;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.TrainWagonChangeType;
import kz.zhsr.app.constants.TrainWagonKind;
import kz.zhsr.app.constants.TrainWagonPlanStatus;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.constants.WagonProblemType;
import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.WagonRef;
import kz.zhsr.app.dto.train.PlanProblem;
import kz.zhsr.app.dto.train.WagonProblem;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Неготовность вагонов по планам - один расчёт для проверок прицепки и отцепки, предупреждений диалогов, готовности
 * рейса и списка проблем планов. Планы считаются будущим, которое сбудется: расчёт идёт по ожидаемому составу поездов (train_wagon,
 * слой «план»).
 * <p>
 * Занятость - пребывание вагона в другом поезде, которое пересекается с нужным промежутком времени. Расположение
 * проверяется, только если занятости нет. Расположение в будущем - текущее расположение, к которому по порядку времени
 * применены запланированные отцепки вагона (вагон на станции отцепки) и прибытия рейсов поездов, в которых вагон едет
 * (вагон на станции прибытия).
 * <p>
 * Противоречие - пересечение двух пребываний, которое не исправить новой отцепкой: у начавшегося раньше пребывания
 * конец уже известен, или оба начинаются в одно время. Если у начавшегося раньше пребывания конца нет, план
 * незавершён - не хватает отцепки: это неготовность, а не отказ.
 */
@Component
public class WagonProblems {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private static final List<Long> TRIPS = List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS);

    /**
     * Где и когда нужен вагон.
     *
     * @param train   поезд, в котором вагон нужен: пребывание вагона в этом поезде занятостью не считается
     * @param station станция, на которой вагон нужен ко времени from; null - расположение не проверяется
     * @param from    начало промежутка
     * @param to      конец промежутка; null - без конца
     */
    public record Need(long wagon, long train, Long station, OffsetDateTime from, OffsetDateTime to) {
    }

    /**
     * Прицепка или отцепка, которую пользователь собирается сохранить.
     *
     * @param changeType   константы TrainWagonChangeType
     * @param fact         true - факт, false - план
     * @param time         время прицепки или отцепки
     * @param station      станция; null - у отцепки: станция, на которой вагон сейчас
     * @param routeStation остановка маршрута рейса; null - вне рейса
     * @param plan         исправляемый или отмечаемый план (train_wagon_change.id); null - новая запись
     */
    public record Change(long train, int changeType, boolean fact, OffsetDateTime time, Long station,
                         Long routeStation, Long plan) {
    }

    /** Потребность одного из будущих планов вагона: рейса или плановой прицепки вне рейса. */
    private record PlanNeed(Need need, OffsetDateTime dt, Long trip, Long trainWagonChange) {
    }

    /** Пребывание вагона в поезде по ожидаемому составу: подряд идущие записи участия одним промежутком. */
    private static final class Stay {
        long train;
        OffsetDateTime beg;
        /** null - отцепка не запланирована */
        OffsetDateTime end;

        boolean overlaps(OffsetDateTime from, OffsetDateTime to) {
            return (to == null || beg.isBefore(to)) && (end == null || end.isAfter(from));
        }
    }

    /**
     * Данные расчёта по набору вагонов: загружаются списками, без запросов на каждый вагон. facts, factAttaches,
     * attaches, trips и detaches - записи по вагону (trips - по поезду); attaches и detaches - ожидающие планы.
     */
    private record Data(Map<Long, List<Stay>> stays, Map<Long, List<DbRec>> facts,
                        Map<Long, List<DbRec>> factAttaches, Map<Long, List<DbRec>> trips,
                        Map<Long, List<DbRec>> attaches, Map<Long, List<DbRec>> detaches, Map<Long, DbRec> places) {
    }

    private final Db db;
    private final RefResolver refResolver;

    public WagonProblems(Db db, RefResolver refResolver) {
        this.db = db;
        this.refResolver = refResolver;
    }

    /**
     * Неготовности по каждой потребности: список того же размера и порядка, что needs; у готового вагона - пустой.
     * Ссылки на вагон, поезд и рейс заполнены.
     */
    public List<List<WagonProblem>> find(List<Need> needs) throws Exception {
        if (needs.isEmpty()) {
            return List.of();
        }
        Data data = load(needs.stream().map(Need::wagon).distinct().toList(), null);
        OffsetDateTime now = OffsetDateTime.now();
        List<List<WagonProblem>> res = needs.stream().map(need -> problems(data, need, now)).toList();
        refResolver.resolve(res);
        return res;
    }

    /**
     * Что будет мешать будущим планам вагонов, если сохранить прицепку или отцепку change: операция применяется к
     * загруженным данным в памяти (в БД ничего не пишется), затем считаются неготовности всех рейсов в статусе «план» и
     * «выполняется» и плановых прицепок вне рейса этих вагонов, включая сохраняемую. Можно ли сохранить операцию, расчёт
     * не решает. Строка - рейс или плановая прицепка вне рейса, у которых есть неготовности, по времени.
     */
    public List<PlanProblem> preview(Change change, List<Long> wagons) throws Exception {
        return preview(wagons, change.train(), (data, wagon) -> apply(data, wagon, change, factTrip(data, change)));
    }

    /** То же при отмене ожидающего плана прицепки или отцепки (запись train_wagon_change). */
    public List<PlanProblem> previewCancel(DbRec plan) throws Exception {
        boolean attach = plan.getInt("change_type") == TrainWagonChangeType.ATTACH;
        long train = plan.getLong("train");
        return preview(List.of(plan.getLong("wagon")), train, (data, wagon) -> {
            List<DbRec> plans = waitingOf(data, wagon, attach);
            if (plans.removeIf(p -> p.getLong("id").equals(plan.getLong("id")))) {
                dropPlan(data, wagon, train, attach, plan.getDateTimeTZ("dt"));
            }
        });
    }

    /** То же при возврате отменённого плана прицепки или отцепки. */
    public List<PlanProblem> previewRestore(DbRec plan) throws Exception {
        return preview(List.of(plan.getLong("wagon")), plan.getLong("train"),
                (data, wagon) -> restore(data, wagon, plan));
    }

    /**
     * То же при удалении факта прицепки или отцепки: факта как не было - вагон снова не в поезде или снова в поезде, а
     * отработанный фактом план снова ожидает.
     */
    public List<PlanProblem> previewUndo(DbRec fact) throws Exception {
        long train = fact.getLong("train");
        Long planId = fact.getLong("plan");
        DbRec plan = planId == null || planId == 0 ? null : db.loadRec("train_wagon_change", planId.longValue());
        return preview(List.of(fact.getLong("wagon")), train, (data, wagon) -> {
            removeFact(data, wagon, fact);
            if (plan != null) {
                restore(data, wagon, plan);
            }
        });
    }

    /** Изменение загруженных данных вагона в памяти. */
    private interface DataChange {
        void apply(Data data, long wagon) throws Exception;
    }

    private List<PlanProblem> preview(List<Long> wagons, long train, DataChange change) throws Exception {
        if (wagons.isEmpty()) {
            return new ArrayList<>();
        }
        Data data = load(wagons, train);
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, PlanProblem> rows = new LinkedHashMap<>();
        for (long wagon : wagons) {
            change.apply(data, wagon);
            for (PlanNeed plan : planNeeds(data, wagon, now)) {
                List<WagonProblem> problems = problems(data, plan.need(), now);
                if (problems.isEmpty()) {
                    continue;
                }
                String key = plan.trip() != null ? "trip " + plan.trip()
                        : plan.need().train() + " " + plan.trainWagonChange() + " " + plan.dt().toInstant();
                rows.computeIfAbsent(key, k -> {
                    PlanProblem row = new PlanProblem();
                    row.setDt(plan.dt());
                    row.setTrain(new TrainRef(plan.need().train()));
                    row.setTrip(plan.trip() == null ? null : new TripRef(plan.trip()));
                    row.setTrainWagonChange(plan.trainWagonChange());
                    row.setProblems(new ArrayList<>());
                    return row;
                }).getProblems().addAll(problems);
            }
        }
        List<PlanProblem> res = new ArrayList<>(rows.values());
        res.sort(Comparator.comparing(PlanProblem::getDt));
        refResolver.resolve(res);
        return res;
    }

    private static List<WagonProblem> problems(Data data, Need need, OffsetDateTime now) {
        List<WagonProblem> problems = new ArrayList<>();
        for (Stay stay : data.stays().getOrDefault(need.wagon(), List.of())) {
            if (stay.train != need.train() && stay.overlaps(need.from(), need.to())) {
                problems.add(busy(data, need, stay));
            }
        }
        if (problems.isEmpty() && need.station() != null) {
            WagonProblem place = location(data, need, now);
            if (place != null) {
                problems.add(place);
            }
        }
        return problems;
    }

    // --- операция в памяти

    /**
     * Изменить загруженные данные вагона так, как их изменит сохранение change: убрать исправляемый или отрабатываемый
     * план, добавить пребывание вагона в поезде или закончить пребывание.
     */
    private static void apply(Data data, long wagon, Change change, Long factTrip) {
        List<Stay> stays = data.stays().computeIfAbsent(wagon, k -> new ArrayList<>());
        boolean attach = change.changeType() == TrainWagonChangeType.ATTACH;
        List<DbRec> plans = waitingOf(data, wagon, attach);
        // исправляемый или отмечаемый план; факт без плана отрабатывает план вагона в этом поезде - того же рейса или
        // наступивший
        DbRec old = plans.stream()
                .filter(p -> change.plan() != null ? p.getLong("id").longValue() == change.plan()
                        : change.fact() && p.getLong("train") == change.train()
                        && (factTrip != null && factTrip.equals(p.getLong("trip"))
                        || !p.getDateTimeTZ("dt").isAfter(change.time())))
                .findFirst().orElse(null);
        if (old != null) {
            plans.remove(old);
            dropPlan(data, wagon, change.train(), attach, old.getDateTimeTZ("dt"));
        }
        // журналы вагона меняет только факт на остановке рейса, который выполняется
        boolean inProgress = change.routeStation() != null && data.trips().getOrDefault(change.train(), List.of())
                .stream().anyMatch(t -> t.getLong("trip_status_type") == TripStatus.IN_PROGRESS);
        DbRec place = data.places().get(wagon);
        if (attach) {
            Stay stay = new Stay();
            stay.train = change.train();
            stay.beg = change.time();
            stay.end = detachAfter(data, wagon, change.train(), change.time());
            stays.add(stay);
            if (!change.fact()) {
                DbRec plan = plan(wagon, change, change.station());
                // trip: признак «в рейсе» - такую прицепку показывает строка рейса
                plan.put("trip", change.routeStation());
                plans.add(plan);
                return;
            }
            DbRec fact = plan(wagon, change, change.station());
            List<DbRec> facts = new ArrayList<>(data.facts().getOrDefault(wagon, List.of()));
            facts.add(fact);
            data.facts().put(wagon, facts);
            List<DbRec> factAttaches = new ArrayList<>(data.factAttaches().getOrDefault(wagon, List.of()));
            factAttaches.removeIf(r -> r.getLong("train") == change.train());
            factAttaches.add(fact);
            data.factAttaches().put(wagon, factAttaches);
            if (inProgress) {
                place.put("location_type", LocationType.TRIP);
                place.put("station", null);
                place.put("place", null);
            }
            return;
        }
        Stay own = stays.stream()
                .filter(s -> s.train == change.train() && !s.beg.isAfter(change.time())
                        && (s.end == null || !s.end.isBefore(change.time())))
                .max(Comparator.comparing(s -> s.beg)).orElse(null);
        if (own == null) {
            return;
        }
        own.end = change.time();
        Long station = change.station() != null ? change.station() : place.getLong("station");
        if (!change.fact()) {
            plans.add(plan(wagon, change, station));
            return;
        }
        List<DbRec> facts = new ArrayList<>(data.facts().getOrDefault(wagon, List.of()));
        facts.removeIf(r -> r.getLong("train") == change.train());
        data.facts().put(wagon, facts);
        if (inProgress) {
            place.put("location_type", LocationType.STATION);
            place.put("station", station);
            place.put("place", station);
        }
    }

    /** Ожидающие планы прицепки или отцепки вагона - списком, который можно менять. */
    private static List<DbRec> waitingOf(Data data, long wagon, boolean attach) {
        Map<Long, List<DbRec>> map = attach ? data.attaches() : data.detaches();
        List<DbRec> res = new ArrayList<>(map.getOrDefault(wagon, List.of()));
        map.put(wagon, res);
        return res;
    }

    /**
     * Убрать действие плана со временем dt из пребываний: у прицепки - пребывание, которое план начинает, у отцепки -
     * конец пребывания.
     */
    private static void dropPlan(Data data, long wagon, long train, boolean attach, OffsetDateTime dt) {
        List<Stay> stays = data.stays().computeIfAbsent(wagon, k -> new ArrayList<>());
        if (attach) {
            stays.stream()
                    .filter(s -> s.train == train && !inTrainByFact(data, wagon, train)
                            && (s.end == null || s.end.isAfter(dt)))
                    .min(Comparator.comparing(s -> s.beg))
                    .ifPresent(stays::remove);
        } else {
            stays.stream()
                    .filter(s -> s.train == train && s.end != null && !s.end.isBefore(dt))
                    .min(Comparator.comparing(s -> s.end))
                    .ifPresent(s -> s.end = null);
        }
    }

    /**
     * Снова ожидающий план. Просроченная прицепка действует с настоящего момента; просроченная отцепка пребывание не
     * заканчивает - как и в остальном расчёте.
     */
    private static void restore(Data data, long wagon, DbRec plan) {
        int type = plan.getInt("change_type");
        long train = plan.getLong("train");
        OffsetDateTime dt = plan.getDateTimeTZ("dt");
        OffsetDateTime now = OffsetDateTime.now();
        Change change = new Change(train, type, false, dt.isAfter(now) ? dt : now, plan.getLong("station"),
                plan.getLong("route_station"), plan.getLong("id"));
        if (type == TrainWagonChangeType.DETACH && !dt.isAfter(now)) {
            waitingOf(data, wagon, false).add(plan(wagon, change, change.station()));
            return;
        }
        apply(data, wagon, change, null);
    }

    /** Убрать факт прицепки или отцепки из загруженных данных вагона. */
    private static void removeFact(Data data, long wagon, DbRec fact) {
        long train = fact.getLong("train");
        OffsetDateTime dt = fact.getDateTimeTZ("dt");
        List<Stay> stays = data.stays().computeIfAbsent(wagon, k -> new ArrayList<>());
        List<DbRec> facts = new ArrayList<>(data.facts().getOrDefault(wagon, List.of()));
        data.facts().put(wagon, facts);
        boolean attach = fact.getInt("change_type") == TrainWagonChangeType.ATTACH;
        if (attach) {
            stays.removeIf(s -> s.train == train && !s.beg.isAfter(OffsetDateTime.now())
                    && (s.end == null || s.end.isAfter(dt)));
            facts.removeIf(r -> r.getLong("train") == train);
        } else {
            // вагон снова в поезде с прицепки, участие которой закончила отцепка
            Stay stay = new Stay();
            stay.train = train;
            stay.beg = data.factAttaches().getOrDefault(wagon, List.of()).stream()
                    .filter(r -> r.getLong("train") == train)
                    .map(r -> r.getDateTimeTZ("dt"))
                    .findFirst().orElse(dt);
            stay.end = detachAfter(data, wagon, train, OffsetDateTime.now());
            stays.add(stay);
            DbRec rec = new DbRec();
            rec.put("wagon", wagon);
            rec.put("train", train);
            facts.add(rec);
        }
        // факт менял журналы вагона: расположение возвращается к прежнему - прицепка в рейсе берёт вагон со станции
        if (fact.get("wagon_location") != null) {
            DbRec place = data.places().get(wagon);
            Long station = attach ? fact.getLong("station") : null;
            place.put("location_type", attach ? LocationType.STATION : LocationType.TRIP);
            place.put("station", station);
            place.put("place", station);
        }
    }

    /**
     * Рейс факта на остановке маршрута - как при сохранении: выполняющийся рейс поезда по маршруту остановки, а если
     * такого нет - ближайший; факт вне рейса и план - null.
     */
    private Long factTrip(Data data, Change change) throws Exception {
        if (!change.fact() || change.routeStation() == null) {
            return null;
        }
        DbRec stop = db.loadRec("route_station", change.routeStation());
        if (stop == null) {
            return null;
        }
        List<DbRec> trips = data.trips().getOrDefault(change.train(), List.of()).stream()
                .filter(t -> t.getLong("route").equals(stop.getLong("route")))
                .toList();
        return trips.stream()
                .filter(t -> t.getLong("trip_status_type") == TripStatus.IN_PROGRESS)
                .findFirst()
                .or(() -> trips.stream().findFirst())
                .map(t -> t.getLong("id"))
                .orElse(null);
    }

    /** Запись сохраняемой прицепки или отцепки с полями записи журнала. */
    private static DbRec plan(long wagon, Change change, Long station) {
        DbRec rec = new DbRec();
        rec.put("id", change.plan());
        rec.put("wagon", wagon);
        rec.put("train", change.train());
        rec.put("dt", change.time());
        rec.put("station", station);
        return rec;
    }

    private static boolean inTrainByFact(Data data, long wagon, long train) {
        return data.facts().getOrDefault(wagon, List.of()).stream().anyMatch(r -> r.getLong("train") == train);
    }

    /** Время первой запланированной отцепки вагона от поезда после time; null - не запланирована. */
    private static OffsetDateTime detachAfter(Data data, long wagon, long train, OffsetDateTime time) {
        return data.detaches().getOrDefault(wagon, List.of()).stream()
                .filter(r -> r.getLong("train") == train)
                .map(r -> r.getDateTimeTZ("dt"))
                .filter(dt -> dt.isAfter(time))
                .min(Comparator.naturalOrder()).orElse(null);
    }

    /**
     * Потребности будущих планов вагона - как в готовности рейса: рейс, на отправлении которого вагон едет в поезде (у
     * выполняющегося - сейчас), рейс, который прицепляет вагон планом на остановке, и плановая прицепка вне рейса.
     */
    private static List<PlanNeed> planNeeds(Data data, long wagon, OffsetDateTime now) {
        List<PlanNeed> res = new ArrayList<>();
        for (Stay stay : data.stays().getOrDefault(wagon, List.of())) {
            for (DbRec trip : data.trips().getOrDefault(stay.train, List.of())) {
                boolean inProgress = trip.getLong("trip_status_type") == TripStatus.IN_PROGRESS;
                OffsetDateTime beg = trip.getDateTimeTZ("beg");
                OffsetDateTime fin = trip.getDateTimeTZ("fin");
                OffsetDateTime departure = inProgress || beg.isBefore(now) ? now : beg;
                OffsetDateTime to = fin.isAfter(departure) ? fin : departure.plusMinutes(1);
                if (!stay.beg.isAfter(departure) && (stay.end == null || stay.end.isAfter(departure))) {
                    Long station = inProgress ? null : trip.getLong("departure_station");
                    res.add(new PlanNeed(new Need(wagon, stay.train, station, departure, to), beg,
                            trip.getLong("id"), null));
                } else if (stay.beg.isAfter(departure) && stay.beg.isBefore(fin)) {
                    data.attaches().getOrDefault(wagon, List.of()).stream()
                            .filter(a -> a.getLong("train") == stay.train && a.getDateTimeTZ("dt").isEqual(stay.beg))
                            .findFirst()
                            .ifPresent(a -> res.add(new PlanNeed(new Need(wagon, stay.train, a.getLong("station"),
                                    stay.beg, to), beg, trip.getLong("id"), null)));
                }
            }
        }
        for (DbRec attach : data.attaches().getOrDefault(wagon, List.of())) {
            if (attach.get("trip") != null) {
                continue;
            }
            long train = attach.getLong("train");
            OffsetDateTime dt = attach.getDateTimeTZ("dt");
            OffsetDateTime from = dt.isAfter(now) ? dt : now;
            res.add(new PlanNeed(new Need(wagon, train, attach.getLong("station"), from,
                    detachAfter(data, wagon, train, from)), dt, null, attach.getLong("id")));
        }
        return res;
    }

    /** Занятость противоречит потребности: пересечение не исправить новой отцепкой. */
    public static boolean contradicts(Need need, WagonProblem busy) {
        if (busy.getWagonProblemType() != WagonProblemType.BUSY) {
            return false;
        }
        if (busy.getDbeg().isEqual(need.from())) {
            return true;
        }
        return need.from().isBefore(busy.getDbeg()) ? need.to() != null : busy.getDend() != null;
    }

    /**
     * Пребывание вагона в поезде, действующее в момент time или кончающееся в этот момент, - как потребность без
     * проверки расположения; нет - null.
     */
    public Need stay(long wagon, long train, OffsetDateTime time) throws Exception {
        Need res = null;
        for (Stay stay : stays(List.of(wagon)).getOrDefault(wagon, List.of())) {
            if (stay.train == train && !stay.beg.isAfter(time) && (stay.end == null || !stay.end.isBefore(time))) {
                res = new Need(wagon, train, null, stay.beg, stay.end);
            }
        }
        return res;
    }

    /** Занятость словами для сообщения об отказе: после какой прицепки вагон занят и когда освободится. */
    public static String busyText(WagonProblem p) {
        StringBuilder msg = new StringBuilder("в этот период он ")
                .append(p.isFact() ? "занят после выполненной" : "будет занят после запланированной")
                .append(" прицепки к поезду ").append(p.getTrain().getName());
        if (p.getTrip() != null) {
            msg.append(" (рейс ").append(p.getTrip().getTrainNumber()).append(' ').append(p.getTrip().getName())
                    .append(')');
        }
        msg.append(" с ").append(format(p.getDbeg()));
        if (p.getDend() != null) {
            msg.append(" и освободится только после запланированной отцепки ").append(format(p.getDend()));
        }
        return msg.toString();
    }

    private static String format(OffsetDateTime time) {
        return time.atZoneSameInstant(ZoneId.systemDefault()).format(TIME);
    }

    // --- загрузка

    private Map<Long, List<Stay>> stays(List<Long> wagons) throws Exception {
        List<DbRec> records = db.loadList("train_wagon", Map.of("wagon", wagons, "kind", TrainWagonKind.PLAN))
                .stream()
                .sorted(Comparator.<DbRec, OffsetDateTime>comparing(r -> r.getDateTimeTZ("dbeg")))
                .toList();
        Map<Long, List<Stay>> res = new LinkedHashMap<>();
        for (DbRec r : records) {
            List<Stay> list = res.computeIfAbsent(r.getLong("wagon"), k -> new ArrayList<>());
            long train = r.getLong("train");
            OffsetDateTime beg = r.getDateTimeTZ("dbeg");
            OffsetDateTime end = r.get("dend") == null ? null : r.getDateTimeTZ("dend");
            // смена позиции в том же поезде продолжает пребывание
            Stay prev = list.stream()
                    .filter(s -> s.train == train && s.end != null && s.end.isEqual(beg))
                    .findFirst().orElse(null);
            if (prev != null) {
                prev.end = end;
                continue;
            }
            Stay stay = new Stay();
            stay.train = train;
            stay.beg = beg;
            stay.end = end;
            list.add(stay);
        }
        // вагон по факту в поезде, а ожидаемый состав это пребывание уже закончил (отцепка просрочена и не отмечена):
        // пребывание без конца
        OffsetDateTime now = OffsetDateTime.now();
        for (DbRec r : facts(wagons)) {
            List<Stay> list = res.computeIfAbsent(r.getLong("wagon"), k -> new ArrayList<>());
            long train = r.getLong("train");
            if (list.stream().anyMatch(s -> s.train == train && !s.beg.isAfter(now)
                    && (s.end == null || s.end.isAfter(now)))) {
                continue;
            }
            list.removeIf(s -> s.train == train && s.end != null && !s.end.isAfter(now));
            Stay stay = new Stay();
            stay.train = train;
            stay.beg = r.getDateTimeTZ("dbeg");
            list.add(stay);
        }
        return res;
    }

    /** Открытые записи участия вагонов по факту: где вагоны сейчас. */
    private List<DbRec> facts(List<Long> wagons) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("wagon", wagons);
        params.put("kind", TrainWagonKind.FACT);
        params.put("dend", null);
        return db.loadList("train_wagon", params);
    }

    /** @param extraTrain поезд, рейсы которого нужны, даже если вагонов в нём нет; null - нет такого */
    private Data load(List<Long> wagons, Long extraTrain) throws Exception {
        Map<Long, List<Stay>> stays = stays(wagons);
        Map<Long, List<DbRec>> facts = UtDb.collectGroupBy_records(facts(wagons), "wagon");
        Map<Long, List<DbRec>> factAttaches = UtDb.collectGroupBy_records(db.loadSql(WagonProblemSql.lastAttaches(),
                Map.of("wagons", wagons, "fact", TrainWagonKind.FACT, "attach", TrainWagonChangeType.ATTACH)),
                "wagon");
        List<Long> trains = Stream.concat(stays.values().stream().flatMap(List::stream).map(s -> s.train),
                Stream.ofNullable(extraTrain)).distinct().toList();
        Map<Long, List<DbRec>> trips = trains.isEmpty() ? Map.of() : UtDb.collectGroupBy_records(
                db.loadSql(WagonProblemSql.trips(), Map.of("trains", trains, "states", TRIPS)), "train");
        Map<Long, DbRec> place = new HashMap<>();
        for (DbRec r : db.loadSql(TrainSql.wagons(), Map.of("wagons", wagons))) {
            place.put(r.getLong("wagon"), r);
        }
        return new Data(stays, facts, factAttaches, trips, waiting(wagons, TrainWagonChangeType.ATTACH),
                waiting(wagons, TrainWagonChangeType.DETACH), place);
    }

    /** Ожидающие планы прицепки или отцепки вагонов по вагонам. */
    private Map<Long, List<DbRec>> waiting(List<Long> wagons, int changeType) throws Exception {
        return UtDb.collectGroupBy_records(db.loadList("train_wagon_change", Map.of("wagon", wagons,
                "kind", TrainWagonKind.PLAN, "plan_status", TrainWagonPlanStatus.WAITING,
                "change_type", changeType)), "wagon");
    }

    // --- занятость

    private static WagonProblem busy(Data data, Need need, Stay stay) {
        WagonProblem p = problem(need, WagonProblemType.BUSY);
        p.setTrain(new TrainRef(stay.train));
        p.setDbeg(stay.beg);
        p.setDend(stay.end);
        // вагон в этом поезде по факту: прицепка выполнена, её время - из журнала
        if (inTrainByFact(data, need.wagon(), stay.train)) {
            p.setFact(true);
            data.factAttaches().getOrDefault(need.wagon(), List.of()).stream()
                    .filter(r -> r.getLong("train") == stay.train)
                    .findFirst()
                    .ifPresent(r -> p.setDbeg(r.getDateTimeTZ("dt")));
        }
        OffsetDateTime start = stay.beg.isAfter(need.from()) ? stay.beg : need.from();
        for (DbRec trip : data.trips().getOrDefault(stay.train, List.of())) {
            if (trip.getDateTimeTZ("fin").isAfter(start)
                    && (stay.end == null || trip.getDateTimeTZ("beg").isBefore(stay.end))
                    && (need.to() == null || trip.getDateTimeTZ("beg").isBefore(need.to()))) {
                p.setTrip(new TripRef(trip.getLong("id")));
                break;
            }
        }
        return p;
    }

    // --- расположение

    private static WagonProblem location(Data data, Need need, OffsetDateTime now) {
        DbRec info = data.places().get(need.wagon());
        Long locationType = info.getLong("location_type");
        Long place = info.getLong("place");
        Long station = info.getLong("station");
        // расположение в будущем: последнее по времени событие планов не позже from
        if (need.from().isAfter(now)) {
            OffsetDateTime last = null;
            Long planned = null;
            for (Stay stay : data.stays().getOrDefault(need.wagon(), List.of())) {
                for (DbRec trip : data.trips().getOrDefault(stay.train, List.of())) {
                    OffsetDateTime fin = trip.getDateTimeTZ("fin");
                    boolean rode = stay.beg.isBefore(fin) && (stay.end == null || !stay.end.isBefore(fin));
                    if (rode && !fin.isAfter(need.from()) && (last == null || fin.isAfter(last))) {
                        last = fin;
                        planned = trip.getLong("arrival_station");
                    }
                }
            }
            for (DbRec detach : data.detaches().getOrDefault(need.wagon(), List.of())) {
                OffsetDateTime dt = detach.getDateTimeTZ("dt");
                if (!dt.isAfter(need.from()) && (last == null || !dt.isBefore(last))) {
                    last = dt;
                    planned = detach.getLong("station");
                }
            }
            if (last != null) {
                locationType = LocationType.STATION;
                place = planned;
                station = planned;
            }
        }
        int type;
        if (!Objects.equals(station, need.station())) {
            type = WagonProblemType.STATION;
        } else if (UtCnv.toLong(locationType) != LocationType.STATION) {
            type = WagonProblemType.YARD;
        } else {
            return null;
        }
        WagonProblem p = problem(need, type);
        p.setLocationType(locationType);
        p.setPlace(place);
        p.setStation(station);
        p.setNeedStation(need.station());
        return p;
    }

    private static WagonProblem problem(Need need, int type) {
        WagonProblem p = new WagonProblem();
        p.setWagon(new WagonRef(need.wagon()));
        p.setWagonProblemType(type);
        p.setNeedDt(need.from());
        return p;
    }

}
