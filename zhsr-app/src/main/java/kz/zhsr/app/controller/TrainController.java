package kz.zhsr.app.controller;

import kz.zhsr.app.dto.train.TrainCard;
import kz.zhsr.app.dto.train.TrainFilter;
import kz.zhsr.app.dto.train.TrainOption;
import kz.zhsr.app.dto.train.TrainRow;
import kz.zhsr.app.dto.train.WagonBusy;
import kz.zhsr.app.service.TrainService;
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
 * Поезд (Ф-ПОЕ-00, Ф-ПОЕ-01, Ф-ПОЕ-02) и прицепка и отцепка вагонов фактом и планом.
 */
@RestController
public class TrainController {

    private final TrainService trainService;

    public TrainController(TrainService trainService) {
        this.trainService = trainService;
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
    public List<WagonBusy> busy(@RequestParam List<String> wagons,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                OffsetDateTime from) throws Exception {
        return trainService.busy(wagons, from);
    }

    @GetMapping("/trains/{id}")
    public TrainCard card(@PathVariable long id) throws Exception {
        return trainService.card(id);
    }

    @PostMapping("/trains")
    public TrainRow create(@RequestBody TrainService.TrainBody body) throws Exception {
        return trainService.create(body);
    }

    @PutMapping("/trains/{id}")
    public TrainRow rename(@PathVariable long id, @RequestBody TrainService.TrainBody body) throws Exception {
        return trainService.rename(id, body);
    }

    @PutMapping("/trains/{id}/order")
    public TrainCard order(@PathVariable long id, @RequestBody List<String> wagons) throws Exception {
        return trainService.order(id, wagons);
    }

    @PostMapping("/trains/{id}/attach")
    public TrainCard attach(@PathVariable long id, @RequestBody TrainService.ChangeBody body) throws Exception {
        return trainService.attach(id, body);
    }

    @PostMapping("/trains/{id}/detach")
    public TrainCard detach(@PathVariable long id, @RequestBody TrainService.ChangeBody body) throws Exception {
        return trainService.detach(id, body);
    }

    /** Отметить плановую прицепку или отцепку выполненной. */
    @PostMapping("/train-changes/{id}/done")
    public TrainCard mark(@PathVariable long id, @RequestBody TrainService.MarkBody body) throws Exception {
        return trainService.mark(id, body);
    }

    /** Отменить план прицепки или отцепки. */
    @PostMapping("/train-changes/{id}/cancel")
    public TrainCard cancelPlan(@PathVariable long id) throws Exception {
        return trainService.cancelPlan(id);
    }

    /** Отменить выполненную прицепку или отцепку без истории. */
    @DeleteMapping("/train-changes/{id}")
    public TrainCard undo(@PathVariable long id) throws Exception {
        return trainService.undo(id);
    }

}
