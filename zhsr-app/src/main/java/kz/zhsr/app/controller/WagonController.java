package kz.zhsr.app.controller;

import kz.zhsr.app.dto.wagon.WagonJournalRow;
import kz.zhsr.app.service.WagonService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Вагон: журнал (Ф-ВАГ-05); список вагонов - в {@link ListController}.
 */
@RestController
public class WagonController {

    private final WagonService wagonService;

    public WagonController(WagonService wagonService) {
        this.wagonService = wagonService;
    }

    /** Журнал вагона за период: from - начало, to - конец, не включая; оба необязательны. */
    @GetMapping("/wagons/{id}/journal")
    public List<WagonJournalRow> journal(@PathVariable long id,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to)
            throws Exception {
        return wagonService.journal(id, from, to);
    }

}
