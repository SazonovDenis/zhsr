package kz.zhsr.app.controller;

import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.trip.ConductorCandidate;
import kz.zhsr.app.dto.trip.StaffCandidate;
import kz.zhsr.app.dto.trip.CouplingWagon;
import kz.zhsr.app.dto.trip.TripCrew;
import kz.zhsr.app.dto.trip.TripRow;
import kz.zhsr.app.dto.trip.WagonCandidate;
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
 * Рейс: создание, правка, отмена, формирование, отправление, прибытие, отцепка и прицепка; список рейсов - в
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
    public List<CouplingWagon> couplingWagons(@PathVariable long id, @RequestParam long type,
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

    @GetMapping("/trips/{id}/wagon-candidates")
    public List<WagonCandidate> wagonCandidates(@PathVariable long id) throws Exception {
        return tripService.wagonCandidates(id);
    }

    @PutMapping("/trips/{id}/conductors")
    public TripCrew setConductors(@PathVariable long id, @RequestBody List<Long> users) throws Exception {
        return tripService.setConductors(id, users);
    }

    @PutMapping("/trips/{id}/crew/{user}/wagons")
    public TripCrew setConductorWagons(@PathVariable long id, @PathVariable long user,
                                       @RequestBody List<String> wagons) throws Exception {
        return tripService.setConductorWagons(id, user, wagons);
    }

    @PutMapping("/trips/{id}/duty/{duty}")
    public TripCrew setDuty(@PathVariable long id, @PathVariable long duty,
                            @RequestBody TripService.DutyBody body) throws Exception {
        return tripService.setDuty(id, duty, body);
    }

    @PutMapping("/trips/{id}/wagons")
    public TripCrew setWagons(@PathVariable long id, @RequestBody List<String> wagons) throws Exception {
        return tripService.setWagons(id, wagons);
    }

    @PostMapping("/trips/{id}/depart")
    public TripCrew depart(@PathVariable long id, @RequestBody TripService.MarkBody body) throws Exception {
        return tripService.depart(id, body);
    }

    @PostMapping("/trips/{id}/arrive")
    public TripCrew arrive(@PathVariable long id, @RequestBody TripService.MarkBody body) throws Exception {
        return tripService.arrive(id, body);
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

}
