package kz.zhsr.app.controller;

import kz.zhsr.app.service.ActCancelService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Отмена акта приёма-передачи (Ф-АКТ-06).
 */
@RestController
public class ActController {

    private final ActCancelService actCancelService;

    public ActController(ActCancelService actCancelService) {
        this.actCancelService = actCancelService;
    }

    /** Отменить акт приёма-передачи с причиной. */
    @PostMapping("/transfers/{id}/cancel")
    public void cancelTransfer(@PathVariable long id, @RequestBody ActCancelService.CancelBody body)
            throws Exception {
        actCancelService.cancelTransfer(id, body);
    }

}
