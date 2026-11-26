package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.TrainWagonChangeType;
import kz.zhsr.app.constants.TrainWagonKind;
import kz.zhsr.app.constants.TrainWagonPlanStatus;
import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.train.PlanProblem;
import kz.zhsr.app.dto.train.WagonProblem;
import kz.zhsr.app.dto.trip.TripFilter;
import kz.zhsr.app.dto.trip.TripOption;
import kz.zhsr.app.dto.trip.TripReadiness;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Проблемы планов (GET /plan-problems): неготовность вагонов от настоящего момента до времени to - для отчёта и
 * дашборда. Расчёт - тот же, что в проверках и в готовности рейса ({@link WagonProblems}).
 */
@Service
public class PlanProblemService {

    private final Db db;
    private final CurrentUserService currentUserService;
    private final ListService listService;
    private final Visibility visibility;
    private final WagonProblems wagonProblems;
    private final RefResolver refResolver;

    public PlanProblemService(Db db, CurrentUserService currentUserService, ListService listService,
                              Visibility visibility, WagonProblems wagonProblems, RefResolver refResolver) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.listService = listService;
        this.visibility = visibility;
        this.wagonProblems = wagonProblems;
        this.refResolver = refResolver;
    }

    /**
     * Рейсы в статусе «план» (с плановым отправлением до to, в том числе просроченным) и «выполняется» и ожидающие
     * плановые прицепки вне рейса с плановым временем не позже to, у вагонов которых есть неготовность, по времени.
     * Рейсы видны по тем же правилам, что в списке рейсов; прицепки вне рейса - только сотрудникам, у которых список
     * рейсов не ограничен депо или своим рейсом.
     */
    public List<PlanProblem> list(OffsetDateTime to) throws Exception {
        CurrentUser me = currentUserService.get();
        List<PlanProblem> res = new ArrayList<>();
        res.addAll(trips(to));
        Visibility.Access access = visibility.access(me, null);
        if (!access.byDepot() && access.trip() == null) {
            res.addAll(attaches(to));
        }
        res.sort(Comparator.comparing(PlanProblem::getDt));
        refResolver.resolve(res);
        return res;
    }

    private List<PlanProblem> trips(OffsetDateTime to) throws Exception {
        List<TripOption> trips = listService.tripOptions(new TripFilter(null, null, null, null, null, to,
                List.of(TripStatus.PLANNED, TripStatus.IN_PROGRESS), null), new ListSort(null, false));
        List<TripReadiness> readiness = trips.stream().map(t -> new TripReadiness(t.getId())).toList();
        refResolver.resolve(readiness);
        List<PlanProblem> res = new ArrayList<>();
        for (int i = 0; i < trips.size(); i++) {
            List<WagonProblem> problems = readiness.get(i).getProblems();
            if (!problems.isEmpty()) {
                PlanProblem row = new PlanProblem();
                row.setDt(trips.get(i).getPlannedDt());
                row.setTrain(new TrainRef(trips.get(i).getTrain().getId()));
                row.setTrip(new TripRef(trips.get(i).getId()));
                row.setProblems(problems);
                res.add(row);
            }
        }
        return res;
    }

    /** Плановые прицепки вне рейса; у просроченной вагон нужен с настоящего момента. */
    private List<PlanProblem> attaches(OffsetDateTime to) throws Exception {
        List<DbRec> plans = db.loadList("train_wagon_change", Map.of("kind", TrainWagonKind.PLAN,
                        "plan_status", TrainWagonPlanStatus.WAITING, "change_type", TrainWagonChangeType.ATTACH))
                .stream()
                .filter(p -> p.get("trip") == null && !p.getDateTimeTZ("dt").isAfter(to))
                .toList();
        OffsetDateTime now = OffsetDateTime.now();
        List<WagonProblems.Need> needs = new ArrayList<>();
        for (DbRec p : plans) {
            long wagon = p.getLong("wagon");
            long train = p.getLong("train");
            OffsetDateTime dt = p.getDateTimeTZ("dt");
            OffsetDateTime from = dt.isAfter(now) ? dt : now;
            OffsetDateTime end = db.loadList("train_wagon_change", Map.of("train", train, "wagon", wagon,
                            "kind", TrainWagonKind.PLAN, "plan_status", TrainWagonPlanStatus.WAITING,
                            "change_type", TrainWagonChangeType.DETACH))
                    .stream()
                    .map(r -> r.getDateTimeTZ("dt"))
                    .filter(d -> d.isAfter(from))
                    .min(Comparator.naturalOrder()).orElse(null);
            needs.add(new WagonProblems.Need(wagon, train, p.getLong("station"), from, end));
        }
        List<List<WagonProblem>> found = wagonProblems.find(needs);
        List<PlanProblem> res = new ArrayList<>();
        for (int i = 0; i < plans.size(); i++) {
            if (!found.get(i).isEmpty()) {
                PlanProblem row = new PlanProblem();
                row.setDt(plans.get(i).getDateTimeTZ("dt"));
                row.setTrain(new TrainRef(plans.get(i).getLong("train")));
                row.setTrainWagonChange(plans.get(i).getLong("id"));
                row.setProblems(found.get(i));
                res.add(row);
            }
        }
        return res;
    }

}
