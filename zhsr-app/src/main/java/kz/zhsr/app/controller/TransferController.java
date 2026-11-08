package kz.zhsr.app.controller;

import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.wagon.TransferPlace;
import kz.zhsr.app.dto.wagon.TransferPlaceWagons;
import kz.zhsr.app.dto.wagon.TransferRow;
import kz.zhsr.app.dto.wagon.TransferWagon;
import kz.zhsr.app.service.TransferService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Акты приёма-передачи вагонов между депо, станцией и парком отстоя (Ф-АКТ-02).
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

    /** Вагоны в расположении «откуда»: параметры station, depot, yard - ровно один. */
    @GetMapping("/transfers/wagons")
    public List<TransferWagon> wagons(TransferPlace from) throws Exception {
        return transferService.wagons(from);
    }

    /** Сколько вагонов стоит в каждом расположении станции. */
    @GetMapping("/transfers/places")
    public List<TransferPlaceWagons> places(@RequestParam long station) throws Exception {
        return transferService.placeWagons(station);
    }

    /** Кто может сдать вагоны из расположения «откуда»: параметры station, depot, yard - ровно один. */
    @GetMapping("/transfers/givers")
    public List<UserRef> givers(TransferPlace from) throws Exception {
        return transferService.givers(from);
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
