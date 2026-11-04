package kz.zhsr.app.controller;

import kz.zhsr.app.dto.ListSort;
import kz.zhsr.app.dto.RouteOption;
import kz.zhsr.app.dto.RouteFilter;
import kz.zhsr.app.dto.RouteRow;
import kz.zhsr.app.dto.TripOption;
import kz.zhsr.app.dto.TripFilter;
import kz.zhsr.app.dto.TripRow;
import kz.zhsr.app.dto.WagonOption;
import kz.zhsr.app.dto.WagonFilter;
import kz.zhsr.app.dto.WagonRow;
import kz.zhsr.app.service.ListService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Списки страниц «Вагоны», «Рейсы» и «Маршруты». Фильтр и сортировка - параметры запроса: поля фильтра
 * (WagonFilter и др.), sort и desc (ListSort). Список значений - повтором параметра:
 * {@code states=1&states=2}.
 */
@RestController
public class ListController {

    private final ListService listService;

    public ListController(ListService listService) {
        this.listService = listService;
    }

    @GetMapping("/wagons")
    public List<WagonRow> wagons(WagonFilter filter, ListSort sort) throws Exception {
        return listService.wagons(filter, sort);
    }

    @GetMapping("/wagons/options")
    public List<WagonOption> wagonOptions(WagonFilter filter, ListSort sort) throws Exception {
        return listService.wagonOptions(filter, sort);
    }

    @GetMapping("/trips")
    public List<TripRow> trips(TripFilter filter, ListSort sort) throws Exception {
        return listService.trips(filter, sort);
    }

    @GetMapping("/trips/options")
    public List<TripOption> tripOptions(TripFilter filter, ListSort sort) throws Exception {
        return listService.tripOptions(filter, sort);
    }

    @GetMapping("/routes")
    public List<RouteRow> routes(RouteFilter filter, ListSort sort) throws Exception {
        return listService.routes(filter, sort);
    }

    @GetMapping("/routes/options")
    public List<RouteOption> routeOptions(RouteFilter filter, ListSort sort) throws Exception {
        return listService.routeOptions(filter, sort);
    }

}
