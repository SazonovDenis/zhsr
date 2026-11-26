package kz.zhsr.app.controller;

import kz.zhsr.app.dto.analytics.GeoChart;
import kz.zhsr.app.dto.train.PlanProblem;
import kz.zhsr.app.dto.train.TrainCard;
import kz.zhsr.app.dto.train.TrainFilter;
import kz.zhsr.app.dto.train.TrainOption;
import kz.zhsr.app.dto.train.TrainRow;
import kz.zhsr.app.dto.train.WagonBusy;
import kz.zhsr.app.service.PlanProblemService;
import kz.zhsr.app.service.TrainService;
import kz.zhsr.app.service.analytics.GeoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Поезд (Ф-ПОЕ-00, Ф-ПОЕ-01, Ф-ПОЕ-02), прицепка и отцепка вагонов и смена порядка вагонов фактом и планом.
 */
@RestController
public class TrainController {

    private final TrainService trainService;
    private final PlanProblemService planProblemService;

    private final GeoService geoService;

    public TrainController(TrainService trainService, PlanProblemService planProblemService, GeoService geoService) {
        this.geoService = geoService;
        this.trainService = trainService;
        this.planProblemService = planProblemService;
    }

    @GetMapping("/trains")
    public List<TrainRow> list(TrainFilter filter) throws Exception {
        return trainService.list(filter);
    }

    @GetMapping("/trains/options")
    public List<TrainOption> options(TrainFilter filter) throws Exception {
        return trainService.options(filter);
    }

    /** Занятость вагонов в поездах с времени from. */
    @GetMapping("/trains/busy")
    public List<WagonBusy> busy(@RequestParam List<Long> wagons,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                OffsetDateTime from) throws Exception {
        return trainService.busy(wagons, from);
    }

    /** Что будет мешать планам вагонов, если сохранить прицепку или отцепку. */
    @GetMapping("/trains/{id}/wagon-problems")
    public List<PlanProblem> wagonProblems(@PathVariable long id, TrainService.ProblemsQuery query) throws Exception {
        return trainService.wagonProblems(id, query);
    }

    /** Что будет мешать планам вагона после отмены плана, возврата плана или удаления факта. */
    @GetMapping("/train-journal/{code}/{id}/wagon-problems")
    public List<PlanProblem> journalProblems(@PathVariable String code, @PathVariable long id,
                                             @RequestParam String action) throws Exception {
        return trainService.journalProblems(code, id, action);
    }

    /** Рейсы и плановые прицепки вне рейса с неготовностью вагонов до времени to. */
    @GetMapping("/plan-problems")
    public List<PlanProblem> planProblems(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                          OffsetDateTime to) throws Exception {
        return planProblemService.list(to);
    }

    @GetMapping("/trains/{id}")
    public TrainCard card(@PathVariable long id) throws Exception {
        return trainService.card(id);
    }

    /** Данные карты поезда. */
    @GetMapping("/trains/{id}/geo")
    public GeoChart geo(@PathVariable long id) throws Exception {
        return geoService.trainChart(id);
    }

    @PostMapping("/trains")
    public TrainRow create(@RequestBody TrainService.TrainBody body) throws Exception {
        return trainService.create(body);
    }

    @PutMapping("/trains/{id}")
    public TrainRow rename(@PathVariable long id, @RequestBody TrainService.TrainBody body) throws Exception {
        return trainService.rename(id, body);
    }

    @PostMapping("/trains/{id}/attach")
    public TrainCard attach(@PathVariable long id, @RequestBody TrainService.ChangeBody body) throws Exception {
        return trainService.attach(id, body);
    }

    @PostMapping("/trains/{id}/detach")
    public TrainCard detach(@PathVariable long id, @RequestBody TrainService.ChangeBody body) throws Exception {
        return trainService.detach(id, body);
    }

    /** Сменить порядок вагонов фактом или планом. */
    @PostMapping("/trains/{id}/seq")
    public TrainCard seq(@PathVariable long id, @RequestBody TrainService.ChangeBody body) throws Exception {
        return trainService.seq(id, body);
    }

    /** Отметить исполнение плана прицепки или отцепки: факт по плану. */
    @PostMapping("/train-journal/{code}/{id}/done")
    public TrainCard mark(@PathVariable String code, @PathVariable long id,
                          @RequestBody TrainService.MarkBody body) throws Exception {
        return trainService.mark(code, id, body);
    }

    /** Отменить план прицепки или отцепки. */
    @PutMapping("/train-journal/{code}/{id}")
    public TrainCard edit(@PathVariable String code, @PathVariable long id,
                          @RequestBody TrainService.ChangeBody body) throws Exception {
        return trainService.edit(code, id, body);
    }

    @PostMapping("/train-journal/{code}/{id}/cancel")
    public TrainCard cancelPlan(@PathVariable String code, @PathVariable long id) throws Exception {
        return trainService.cancelPlan(code, id);
    }

    /** Вернуть отменённый план прицепки или отцепки. */
    @PostMapping("/train-journal/{code}/{id}/restore")
    public TrainCard restorePlan(@PathVariable String code, @PathVariable long id) throws Exception {
        return trainService.restorePlan(code, id);
    }

    /** Удалить ошибочный факт прицепки или отцепки без истории. */
    @DeleteMapping("/train-journal/{code}/{id}")
    public TrainCard undo(@PathVariable String code, @PathVariable long id) throws Exception {
        return trainService.undo(code, id);
    }


    /** Удалить, если на запись никто не ссылается: иначе 409 с причиной. */
    @DeleteMapping("/trains/{id}")
    public void delete(@PathVariable long id) throws Exception {
        trainService.delete(id);
    }
}
