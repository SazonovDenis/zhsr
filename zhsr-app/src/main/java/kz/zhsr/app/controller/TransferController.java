package kz.zhsr.app.controller;

import kz.zhsr.app.dto.StaffCandidate;
import kz.zhsr.app.dto.TransferRow;
import kz.zhsr.app.dto.TransferWagon;
import kz.zhsr.app.service.TransferService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Акты приёма-передачи вагонов (Ф-АКТ-02).
 */
@RestController
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping("/transfers")
    public List<TransferRow> list(@RequestParam(required = false) List<Long> states) throws Exception {
        return transferService.list(states);
    }

    @GetMapping("/transfers/wagons")
    public List<TransferWagon> wagons(@RequestParam long direction, @RequestParam(required = false) Long station,
                                      @RequestParam(required = false) Long depot,
                                      @RequestParam(required = false) Long yard) throws Exception {
        return transferService.wagons(direction, station, depot, yard);
    }

    @GetMapping("/transfers/givers")
    public List<StaffCandidate> givers(@RequestParam long direction, @RequestParam long station) throws Exception {
        return transferService.givers(direction, station);
    }

    @PostMapping("/transfers")
    public TransferRow create(@RequestBody TransferService.TransferBody body) throws Exception {
        return transferService.create(body);
    }

    @PostMapping("/transfers/{id}/accept")
    public TransferRow accept(@PathVariable long id) throws Exception {
        return transferService.accept(id);
    }

    @PostMapping("/transfers/{id}/reject")
    public TransferRow reject(@PathVariable long id, @RequestBody TransferService.RejectBody body) throws Exception {
        return transferService.reject(id, body);
    }

}
