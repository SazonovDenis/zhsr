package kz.zhsr.app.controller;

import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.trip.ConductorCandidate;
import kz.zhsr.app.dto.trip.StaffCandidate;
import kz.zhsr.app.dto.trip.CouplingWagon;
import kz.zhsr.app.dto.trip.TripCrew;
import kz.zhsr.app.dto.trip.TripResponsibles;
import kz.zhsr.app.dto.trip.TripRow;
import kz.zhsr.app.service.CouplingService;
import kz.zhsr.app.service.TripService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Рейс: создание, правка, отмена, бригада, отправление, прибытие, прицепка и отцепка в рейсе; вагоны поезда - в
 * {@link TrainController}; список рейсов - в
 * {@link ListController}.
 */
@RestController
public class TripController {

    private final TripService tripService;
    private final CouplingService couplingService;

    public TripController(TripService tripService, CouplingService couplingService) {
        this.tripService = tripService;
        this.couplingService = couplingService;
    }

    @PostMapping("/trips")
    public TripRow create(@RequestBody TripService.TripBody body) throws Exception {
        return tripService.create(body);
    }

    @PutMapping("/trips/{id}")
    public TripRow update(@PathVariable long id, @RequestBody TripService.TripBody body) throws Exception {
        return tripService.update(id, body);
    }

    @PostMapping("/trips/{id}/cancel")
    public TripRow cancel(@PathVariable long id) throws Exception {
        return tripService.cancel(id);
    }

    @GetMapping("/trips/{id}/coupling-wagons")
    public List<CouplingWagon> couplingWagons(@PathVariable long id, @RequestParam int type,
                                              @RequestParam(required = false) Long station) throws Exception {
        return couplingService.wagons(id, type, station);
    }

    @PostMapping("/trips/{id}/coupling")
    public TripCrew couple(@PathVariable long id, @RequestBody CouplingService.CouplingBody body) throws Exception {
        return couplingService.couple(id, body);
    }

    @GetMapping("/trips/{id}/crew")
    public TripCrew crew(@PathVariable long id) throws Exception {
        return tripService.crew(id);
    }

    @GetMapping("/trips/{id}/conductor-candidates")
    public List<ConductorCandidate> conductorCandidates(@PathVariable long id) throws Exception {
        return tripService.conductorCandidates(id);
    }

    @GetMapping("/trips/{id}/staff-candidates")
    public List<StaffCandidate> staffCandidates(@PathVariable long id, @RequestParam long duty) throws Exception {
        return tripService.staffCandidates(id, duty);
    }


    @PutMapping("/trips/{id}/crew")
    public TripCrew setCrew(@PathVariable long id, @RequestBody TripService.CrewBody body) throws Exception {
        return tripService.setCrew(id, body);
    }

    @PostMapping("/trips/{id}/depart")
    public TripCrew depart(@PathVariable long id, @RequestBody TripService.MarkBody body) throws Exception {
        return tripService.depart(id, body);
    }

    @PostMapping("/trips/{id}/arrive")
    public TripCrew arrive(@PathVariable long id, @RequestBody TripService.MarkBody body) throws Exception {
        return tripService.arrive(id, body);
    }

    @GetMapping("/trips/{id}/responsibles")
    public TripResponsibles responsibles(@PathVariable long id, @RequestParam(required = false) Integer layer)
            throws Exception {
        return tripService.responsibles(id, layer);
    }

    @PostMapping("/trips/{id}/responsibles")
    public TripResponsibles saveResponsibles(@PathVariable long id, @RequestBody TripService.ResponsiblesBody body)
            throws Exception {
        return tripService.saveResponsibles(id, body);
    }

    @PutMapping("/trips/{id}/depart")
    public TripCrew retimeDeparture(@PathVariable long id, @RequestBody TripService.MarkBody body) throws Exception {
        return tripService.retimeDeparture(id, body);
    }

    @DeleteMapping("/trips/{id}/depart")
    public TripCrew cancelDeparture(@PathVariable long id) throws Exception {
        return tripService.cancelDeparture(id);
    }

    @PutMapping("/trips/{id}/arrive")
    public TripCrew retimeArrival(@PathVariable long id, @RequestBody TripService.MarkBody body) throws Exception {
        return tripService.retimeArrival(id, body);
    }

    @DeleteMapping("/trips/{id}/arrive")
    public TripCrew cancelArrival(@PathVariable long id) throws Exception {
        return tripService.cancelArrival(id);
    }


    /** Удалить, если на запись никто не ссылается: иначе 409 с причиной. */
    @DeleteMapping("/trips/{id}")
    public void delete(@PathVariable long id) throws Exception {
        tripService.delete(id);
    }
}
