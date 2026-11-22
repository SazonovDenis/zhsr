package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.constants.LocationType;
import kz.zhsr.app.constants.TripResponsibleLayer;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.constants.WagonResponsibleKind;
import kz.zhsr.app.constants.WagonResponsiblePlanStatus;
import kz.zhsr.app.constants.WagonResponsibleTripPoint;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.ref.WagonRef;
import kz.zhsr.app.dto.trip.TripCrew;
import kz.zhsr.app.dto.trip.TripResponsibles;
import kz.zhsr.app.dto.wagon.ResponsibleCandidate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ответственные за вагоны рейса (Ф-РЕЙ-08) в журнале ответственных. Бригада рейса - роль на рейсе, а за какие вагоны
 * отвечает сотрудник, хранит только журнал. План связан с рейсом ссылкой и стадией (wagon_responsible.trip,
 * trip_point): план ответственных на рейс - стадия «отправление», план ответственных после рейса - «прибытие»; начало
 * плана - справочно, время стадии по плану рейса. План на рейс выбирают из бригады
 * рейса, план после рейса - из бригады и сотрудников депо станции прибытия. Отметка отправления отрабатывает план на
 * рейс, отметка прибытия - план после рейса. План правится, пока его не отработали: план на рейс - до отправления,
 * план после рейса - до прибытия. Права проверяет вызывающий ({@link TripService}).
 */
@Service
public class TripResponsibleService {

    /**
     * Ответственные одного вагона.
     *
     * @param wagon вагон (wagon.id)
     * @param usrs  ответственные; пусто - ответственный не назначен (у плана после рейса - плана нет)
     */
    public record WagonUsrs(long wagon, List<Long> usrs) {
    }

    private final Db db;
    private final WagonJournal wagonJournal;
    private final RefResolver refResolver;

    public TripResponsibleService(Db db, WagonJournal wagonJournal, RefResolver refResolver) {
        this.db = db;
        this.wagonJournal = wagonJournal;
        this.refResolver = refResolver;
    }

    /**
     * Ответственные на рейс по сотрудникам: рейс в статусе «план» - план на рейс, «выполняется» - текущий факт,
     * «выполнен» - факт на момент прибытия.
     *
     * @param wagons вагоны, которые едут в поезде рейса
     * @return сотрудник (usr) - номера вагонов, по номеру
     */
    Map<Long, List<String>> crewWagons(long trip, List<Long> wagons) throws Exception {
        Map<Long, List<String>> res = new HashMap<>();
        if (wagons.isEmpty()) {
            return res;
        }
        Map<String, Object> params = TripLayerSql.params();
        params.put("trip", trip);
        params.put("wagons", wagons);
        for (DbRec r : db.loadSql(TripLayerSql.crewWagons(), params)) {
            res.computeIfAbsent(r.getLong("usr"), k -> new ArrayList<>()).add(r.getString("number"));
        }
        return res;
    }

    /**
     * Ответственные за вагоны рейса.
     *
     * @param crew           рейс с вагонами и бригадой
     * @param layer          что показать: TripResponsibleLayer
     * @param readonlyReason почему пользователь не может менять; null - может
     */
    public TripResponsibles view(TripCrew crew, int layer, String readonlyReason) throws Exception {
        long status = crew.getTrip().getTripStatusType();
        boolean plan = isPlan(layer, status);
        TripResponsibles res = new TripResponsibles();
        res.setTrip(crew.getTrip().getId());
        res.setTripStatusType(status);
        res.setLayer(layer);
        res.setPlan(plan);
        res.setStation(station(crew, layer));

        List<TripCrew.Wagon> wagons = wagons(crew, layer);
        Map<Long, List<Long>> shown = new HashMap<>();
        Map<Long, OffsetDateTime> starts = new HashMap<>();
        long trip = crew.getTrip().getId();
        if (plan) {
            OffsetDateTime start = planStart(crew, layer);
            for (TripCrew.Wagon w : wagons) {
                List<Long> usrs = wagonJournal.waitingPlanUsrs(w.getWagon(), trip, planPoint(layer));
                shown.put(w.getWagon(), usrs == null ? List.of() : usrs);
                if (usrs != null) {
                    starts.put(w.getWagon(), start);
                }
            }
        } else {
            shown.putAll(wagonJournal.currentUsrs(wagons.stream().map(TripCrew.Wagon::getWagon).toList()));
            starts.putAll(starts(wagons));
            if (layer == TripResponsibleLayer.ARRIVAL) {
                // у вагона с планом после рейса - план: его и отработает прибытие
                OffsetDateTime start = crew.getTrip().getArrival();
                for (TripCrew.Wagon w : wagons) {
                    List<Long> usrs = wagonJournal.waitingPlanUsrs(w.getWagon(), trip,
                            WagonResponsibleTripPoint.ARRIVAL);
                    if (usrs != null) {
                        shown.put(w.getWagon(), usrs);
                        starts.put(w.getWagon(), start);
                    }
                }
            }
        }
        List<TripResponsibles.Wagon> rows = new ArrayList<>();
        for (TripCrew.Wagon w : wagons) {
            TripResponsibles.Wagon row = new TripResponsibles.Wagon();
            row.setWagon(new WagonRef(w.getWagon()));
            row.setSeq(w.getSeq());
            row.setDbeg(starts.get(w.getWagon()));
            row.setUsrs(shown.get(w.getWagon()).stream().map(UserRef::new).toList());
            rows.add(row);
        }
        res.setWagons(rows);
        res.setReadonlyReason(readonlyReason == null ? statusReadonly(layer, status) : readonlyReason);
        res.setCandidates(candidates(crew, res.getStation()));

        refResolver.resolve(rows.stream().map(TripResponsibles.Wagon::getWagon).toList());
        refResolver.resolve(rows.stream().flatMap(r -> r.getUsrs().stream()).toList());
        refResolver.resolve(res.getCandidates());
        return res;
    }

    /** Почему слой нельзя менять в этом статусе рейса; можно - null. */
    static String statusReadonly(int layer, long status) {
        if (status == TripStatus.CANCELLED) {
            return "Рейс отменён";
        }
        if (layer == TripResponsibleLayer.AFTER && status != TripStatus.PLANNED && status != TripStatus.IN_PROGRESS) {
            return "План ответственных после рейса правится до прибытия";
        }
        return null;
    }

    /**
     * Записать изменения ответственных слоя layer: план на рейс (рейс в статусе «план») и план после рейса - планом,
     * ответственные на рейс в пути и после прибытия и слой «факт» в любом статусе - фактом временем time. Меняются только вагоны, где состав
     * отличается от показанного. Вызывается внутри транзакции.
     *
     * @param layer   TripResponsibleLayer.TRIP, AFTER или FACT
     * @param time    время факта; у плана не используется
     * @param changes ответственные вагонов; null или пусто - ничего не менять
     * @throws ApiException 400 - вагон не из рейса или сотрудника нельзя назначить; 409 - слой не меняется в этом
     *                      статусе
     */
    public void save(TripCrew crew, int layer, OffsetDateTime time, List<WagonUsrs> changes) throws Exception {
        if (layer != TripResponsibleLayer.TRIP && layer != TripResponsibleLayer.AFTER
                && layer != TripResponsibleLayer.FACT) {
            throw ApiException.badRequest("Неизвестный слой ответственных: " + layer);
        }
        long status = crew.getTrip().getTripStatusType();
        String reason = statusReadonly(layer, status);
        if (reason != null) {
            throw ApiException.conflict(reason);
        }
        if (changes == null || changes.isEmpty()) {
            return;
        }
        Set<Long> wagons = new HashSet<>();
        wagons(crew, layer).forEach(w -> wagons.add(w.getWagon()));
        Set<Long> allowed = new HashSet<>();
        candidates(crew, station(crew, layer)).forEach(c -> allowed.add(c.getId()));
        boolean plan = isPlan(layer, status);
        long trip = crew.getTrip().getId();
        OffsetDateTime start = plan ? planStart(crew, layer) : null;
        Map<Long, List<Long>> current = plan ? null
                : wagonJournal.currentUsrs(changes.stream().map(WagonUsrs::wagon).toList());
        for (WagonUsrs change : changes) {
            if (!wagons.contains(change.wagon())) {
                throw ApiException.badRequest("Вагон не из поезда рейса");
            }
            List<Long> usrs = change.usrs() == null ? List.of() : change.usrs();
            List<Long> was = plan ? wagonJournal.waitingPlanUsrs(change.wagon(), trip, planPoint(layer))
                    : current.get(change.wagon());
            if (was == null) {
                was = List.of();
            }
            if (WagonJournal.sameUsrs(usrs, was)) {
                continue;
            }
            // уже назначенных можно оставить, даже если их нет среди тех, кого можно назначить
            for (Long usr : usrs) {
                if (!allowed.contains(usr) && !was.contains(usr)) {
                    throw ApiException.badRequest(layer == TripResponsibleLayer.TRIP && plan
                            ? "На рейс ответственных выбирают из бригады рейса"
                            : "Этого сотрудника нельзя назначить ответственным за вагон");
                }
            }
            if (!plan) {
                wagonJournal.assign(change.wagon(), time, usrs, trip, null);
            } else if (layer == TripResponsibleLayer.AFTER && usrs.isEmpty()) {
                // без ответственных после рейса - плана нет: после прибытия остаются ответственные рейса
                wagonJournal.cancelPlan(change.wagon(), trip, WagonResponsibleTripPoint.ARRIVAL);
            } else {
                wagonJournal.setPlan(change.wagon(), trip, planPoint(layer), start, usrs);
            }
        }
    }

    /**
     * Отработать план на рейс при отправлении: у каждого вагона, который едет в поезде, - факт временем time с
     * составом из правки в окне отметки (overrides), а если её нет - из плана; вагон без плана и без правки остаётся
     * без ответственных: в рейсе отвечает бригада. План - «отработан». Состав совпал с текущим - факт не меняется.
     * Вызывается внутри транзакции после перевода вагонов в рейс.
     *
     * @param crew      рейс после отправления
     * @param overrides правки в окне отметки; null или пусто - только план
     * @throws ApiException 400 - вагон не из рейса или сотрудника нельзя назначить
     */
    public void markDeparture(TripCrew crew, OffsetDateTime time, List<WagonUsrs> overrides) throws Exception {
        mark(crew, wagons(crew, TripResponsibleLayer.TRIP), WagonResponsibleTripPoint.DEPARTURE, null, time, overrides,
                List.of());
    }

    /**
     * Отработать план после рейса при прибытии: у каждого вагона, который доехал, - факт временем time с составом из
     * правки в окне отметки (overrides), а если её нет - из плана после рейса; вагон без плана и без правки не
     * меняется. План - «отработан». Вызывается внутри транзакции после перевода вагонов на станцию прибытия.
     *
     * @param crew рейс после прибытия
     * @throws ApiException 400 - вагон не из рейса или сотрудника нельзя назначить
     */
    public void markArrival(TripCrew crew, OffsetDateTime time, List<WagonUsrs> overrides) throws Exception {
        mark(crew, wagons(crew, TripResponsibleLayer.TRIP), WagonResponsibleTripPoint.ARRIVAL,
                crew.getTrip().getArrivalStation(), time, overrides, null);
    }

    /**
     * Отметка по плану стадии point рейса: факты отметки - с рейсом и стадией.
     *
     * @param station  станция, сотрудники депо которой могут быть ответственными; null - только бригада
     * @param fallback состав вагона без плана и без правки; null - вагон не меняется
     */
    private void mark(TripCrew crew, List<TripCrew.Wagon> wagons, int point, Long station,
                      OffsetDateTime time, List<WagonUsrs> overrides, List<Long> fallback) throws Exception {
        Map<Long, List<Long>> edited = new HashMap<>();
        if (overrides != null) {
            overrides.forEach(o -> edited.put(o.wagon(), o.usrs() == null ? List.of() : o.usrs()));
        }
        Set<Long> ids = new HashSet<>();
        wagons.forEach(w -> ids.add(w.getWagon()));
        if (!ids.containsAll(edited.keySet())) {
            throw ApiException.badRequest("Вагон не из поезда рейса");
        }
        Set<Long> allowed = new HashSet<>();
        candidates(crew, station).forEach(c -> allowed.add(c.getId()));
        Map<Long, List<Long>> current = wagonJournal.currentUsrs(List.copyOf(ids));
        long trip = crew.getTrip().getId();
        for (TripCrew.Wagon w : wagons) {
            long wagon = w.getWagon();
            List<Long> plan = wagonJournal.waitingPlanUsrs(wagon, trip, point);
            List<Long> usrs = edited.containsKey(wagon) ? edited.get(wagon) : plan != null ? plan : fallback;
            if (plan != null) {
                wagonJournal.switchPlan(wagon, trip, point, WagonResponsiblePlanStatus.WAITING,
                        WagonResponsiblePlanStatus.DONE);
            }
            if (usrs == null || WagonJournal.sameUsrs(usrs, current.get(wagon))) {
                continue;
            }
            for (Long usr : usrs) {
                if (!allowed.contains(usr) && !current.get(wagon).contains(usr)
                        && (plan == null || !plan.contains(usr))) {
                    throw ApiException.badRequest("Этого сотрудника нельзя назначить ответственным за вагон");
                }
            }
            wagonJournal.assign(wagon, time, usrs, trip, point);
        }
    }

    /**
     * Отменить отработку плана при отмене отметки стадии point рейса trip: факты, записанные отметкой, удаляются,
     * прежние снова текущие; план стадии - снова «ожидает». Вызывается внутри транзакции.
     *
     * @param marked время отметки
     */
    public void unmark(long trip, int point, List<Long> wagons, OffsetDateTime marked) throws Exception {
        for (Long wagon : wagons) {
            wagonJournal.unassignMark(wagon, trip, point, marked);
            wagonJournal.switchPlan(wagon, trip, point, WagonResponsiblePlanStatus.DONE,
                    WagonResponsiblePlanStatus.WAITING);
        }
    }

    /** Перенести время фактов ответственных, записанных отметкой стадии point рейса trip, на исправленное время. */
    public void retimeMarks(long trip, int point, List<Long> wagons, OffsetDateTime from, OffsetDateTime to)
            throws Exception {
        for (Long wagon : wagons) {
            wagonJournal.retimeMark(wagon, trip, point, from, to);
        }
    }

    /**
     * Перенести начало ожидающих планов рейса на его новые плановые отправление и прибытие (после правки рейса): начало
     * плана справочное, план ищется по рейсу и стадии. Вызывается внутри транзакции.
     */
    void retimePlans(TripCrew crew) throws Exception {
        long trip = crew.getTrip().getId();
        retimePlan(trip, WagonResponsibleTripPoint.DEPARTURE, crew.getTrip().getPlannedDt());
        retimePlan(trip, WagonResponsibleTripPoint.ARRIVAL, crew.getTrip().getArrival());
    }

    private void retimePlan(long trip, int point, OffsetDateTime dbeg) throws Exception {
        db.updateRec("wagon_responsible", Map.of("dbeg", dbeg), Map.of("trip", trip, "trip_point", point,
                "kind", WagonResponsibleKind.PLAN, "plan_status", WagonResponsiblePlanStatus.WAITING));
    }

    /** Отменить ожидающие планы рейса - на рейс и после рейса - у всех вагонов. Вызывается внутри транзакции. */
    void cancelPlans(long trip) throws Exception {
        db.updateRec("wagon_responsible", Map.of("plan_status", WagonResponsiblePlanStatus.CANCELLED),
                Map.of("trip", trip, "kind", WagonResponsibleKind.PLAN,
                        "plan_status", WagonResponsiblePlanStatus.WAITING));
    }

    /**
     * Убрать сотрудников, которые ушли из бригады, из планов рейса: план на рейс без них - «ответственных нет», план
     * после рейса без них - отменяется. Вызывается внутри транзакции.
     */
    void dropFromPlans(TripCrew crew, Set<Long> usrs) throws Exception {
        if (usrs.isEmpty()) {
            return;
        }
        long trip = crew.getTrip().getId();
        for (TripCrew.Wagon w : crew.getWagons()) {
            drop(w.getWagon(), trip, WagonResponsibleTripPoint.DEPARTURE, crew.getTrip().getPlannedDt(), usrs,
                    false);
            drop(w.getWagon(), trip, WagonResponsibleTripPoint.ARRIVAL, crew.getTrip().getArrival(), usrs, true);
        }
    }

    private void drop(long wagon, long trip, int point, OffsetDateTime dbeg, Set<Long> usrs, boolean cancelEmpty)
            throws Exception {
        List<Long> plan = wagonJournal.waitingPlanUsrs(wagon, trip, point);
        if (plan == null || plan.stream().noneMatch(usrs::contains)) {
            return;
        }
        List<Long> rest = plan.stream().filter(u -> !usrs.contains(u)).toList();
        if (rest.isEmpty() && cancelEmpty) {
            wagonJournal.cancelPlan(wagon, trip, point);
        } else {
            wagonJournal.setPlan(wagon, trip, point, dbeg, rest);
        }
    }

    /**
     * Вагон больше не едет в рейсах поезда в статусе «план» с плановым отправлением не раньше from: планы этих рейсов
     * у вагона - на рейс и после рейса - отменяются. Вызывается внутри транзакции.
     *
     * @param from null - во всех рейсах поезда в статусе «план»
     */
    void releasePlanned(long train, long wagon, OffsetDateTime from) throws Exception {
        for (DbRec trip : db.loadSql(TripSql.trainTrips(), Map.of("train", train, "states",
                List.of(TripStatus.PLANNED)))) {
            if (from == null || !trip.getDateTimeTZ("beg").isBefore(from)) {
                wagonJournal.cancelPlan(wagon, trip.getLong("id"), WagonResponsibleTripPoint.DEPARTURE);
                wagonJournal.cancelPlan(wagon, trip.getLong("id"), WagonResponsibleTripPoint.ARRIVAL);
            }
        }
    }

    /**
     * Вагон ушёл из поезда выполняющегося рейса: план после рейса у вагона отменяется - вагон не доедет до станции
     * прибытия с поездом. Вызывается внутри транзакции.
     */
    void releaseAfter(long trip, long wagon) throws Exception {
        wagonJournal.cancelPlan(wagon, trip, WagonResponsibleTripPoint.ARRIVAL);
    }

    /** Показан ли план: план после рейса всегда, на рейс - до отправления; слой «факт» - никогда. */
    private static boolean isPlan(int layer, long status) {
        return layer == TripResponsibleLayer.AFTER
                || (layer == TripResponsibleLayer.TRIP && status == TripStatus.PLANNED);
    }

    /** Стадия рейса плана слоя: на рейс - отправление, после рейса - прибытие. */
    private static int planPoint(int layer) {
        return layer == TripResponsibleLayer.AFTER ? WagonResponsibleTripPoint.ARRIVAL
                : WagonResponsibleTripPoint.DEPARTURE;
    }

    /** Начало плана: на рейс - плановое отправление, после рейса - плановое прибытие. */
    private static OffsetDateTime planStart(TripCrew crew, int layer) {
        return layer == TripResponsibleLayer.AFTER ? crew.getTrip().getArrival() : crew.getTrip().getPlannedDt();
    }

    /**
     * Вагоны слоя по позиции. Планы - у вагонов, которые едут в поезде. Факт: у рейса в статусе «план» - вагоны,
     * которые поедут в поезде и ещё не в другом рейсе; у выполняющегося - вагоны, которые едут в поезде; у
     * выполненного - доехавшие и не ушедшие в другой рейс.
     */
    private static List<TripCrew.Wagon> wagons(TripCrew crew, int layer) {
        long trip = crew.getTrip().getId();
        long status = crew.getTrip().getTripStatusType();
        boolean plan = isPlan(layer, status);
        return crew.getWagons().stream()
                .filter(TripCrew.Wagon::isRiding)
                .filter(w -> plan || (status == TripStatus.IN_PROGRESS
                        ? w.getLocationType() == LocationType.TRIP && w.getTrip() != null && w.getTrip().getId() == trip
                        : w.getLocationType() != LocationType.TRIP))
                .sorted(Comparator.comparingInt(TripCrew.Wagon::getSeq))
                .toList();
    }

    /**
     * Станция, сотрудники депо которой могут быть ответственными: после рейса, в окне прибытия и у выполненного рейса
     * - станция прибытия; факт до отправления - станция отправления; на рейс и факт в пути - нет, только бригада.
     */
    private static Long station(TripCrew crew, int layer) {
        if (layer == TripResponsibleLayer.FACT) {
            long status = crew.getTrip().getTripStatusType();
            if (status == TripStatus.PLANNED) {
                return crew.getTrip().getDepartureStation();
            }
            return status == TripStatus.IN_PROGRESS ? null : crew.getTrip().getArrivalStation();
        }
        if (layer == TripResponsibleLayer.TRIP && crew.getTrip().getTripStatusType() != TripStatus.DONE) {
            return null;
        }
        return crew.getTrip().getArrivalStation();
    }

    /** Бригада рейса, затем сотрудники депо станции; по ФИО. */
    private List<ResponsibleCandidate> candidates(TripCrew crew, Long station) throws Exception {
        Map<Long, ResponsibleCandidate> res = new LinkedHashMap<>();
        for (TripCrew.Member m : crew.getCrew()) {
            ResponsibleCandidate c = res.computeIfAbsent(m.getUsr().getId(), ResponsibleCandidate::candidate);
            c.setCrewDutyType(m.getCrewDutyType());
        }
        if (station != null) {
            for (DbRec r : db.loadSql(WagonSql.depotStaff(), Map.of("station", station))) {
                res.computeIfAbsent(r.getLong("id"), ResponsibleCandidate::candidate).setDepotStaff(true);
            }
        }
        return new ArrayList<>(res.values());
    }

    /** Начало текущих ответственных вагонов: dbeg открытой записи факта. */
    private Map<Long, OffsetDateTime> starts(List<TripCrew.Wagon> wagons) throws Exception {
        Map<Long, OffsetDateTime> res = new HashMap<>();
        List<Long> ids = wagons.stream().map(TripCrew.Wagon::getWagon).toList();
        if (ids.isEmpty()) {
            return res;
        }
        for (DbRec info : db.loadSql(WagonSql.currentResponsibleStarts(), Map.of("wagons", ids))) {
            res.put(info.getLong("wagon"), info.getDateTimeTZ("dbeg"));
        }
        return res;
    }

}
