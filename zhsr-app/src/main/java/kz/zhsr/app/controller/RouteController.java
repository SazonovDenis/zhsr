package kz.zhsr.app.controller;

import kz.zhsr.app.dto.NextStation;
import kz.zhsr.app.dto.RecalcStops;
import kz.zhsr.app.dto.Route;
import kz.zhsr.app.dto.RouteDraft;
import kz.zhsr.app.dto.RouteSave;
import kz.zhsr.app.dto.RouteStop;
import kz.zhsr.app.service.RouteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Карточка маршрута, создание, сохранение и помощь редактору (следующие станции, пересчёт остановок); список
 * маршрутов - в {@link ListController}.
 */
@RestController
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping("/routes/{id}")
    public Route route(@PathVariable long id) throws Exception {
        return routeService.route(id);
    }

    @PostMapping("/routes")
    public Route create(@RequestBody RouteDraft draft) throws Exception {
        return routeService.create(draft);
    }

    @PutMapping("/routes/{id}")
    public Route save(@PathVariable long id, @RequestBody RouteSave body) throws Exception {
        return routeService.save(id, body);
    }

    @GetMapping("/routes/next-stations")
    public List<NextStation> nextStations(@RequestParam(required = false) Long station,
                                          @RequestParam(required = false) Integer km) throws Exception {
        return routeService.nextStations(station, km);
    }

    @PostMapping("/routes/recalc-stops")
    public List<RouteStop> recalc(@RequestBody RecalcStops body) throws Exception {
        return routeService.recalc(body);
    }

}
