package kz.zhsr.app.controller;

import kz.zhsr.app.dto.wagon.ResponsibleCandidate;
import kz.zhsr.app.dto.wagon.WagonJournalRow;
import kz.zhsr.app.dto.wagon.WagonRow;
import kz.zhsr.app.service.ResponsibleService;
import kz.zhsr.app.service.WagonEditService;
import kz.zhsr.app.service.WagonEntryService;
import kz.zhsr.app.service.WagonService;
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
 * Вагон: ввод (Ф-ВАГ-01), исправление данных (Ф-ВАГ-08), ручная смена состояния и расположения (Ф-ВАГ-02, Ф-ВАГ-03), журнал (Ф-ВАГ-05), кого можно назначить ответственным; список вагонов - в {@link ListController}.
 */
@RestController
public class WagonController {

    private final WagonService wagonService;
    private final ResponsibleService responsibleService;
    private final WagonEntryService wagonEntryService;
    private final WagonEditService wagonEditService;

    public WagonController(WagonService wagonService, ResponsibleService responsibleService,
                           WagonEntryService wagonEntryService, WagonEditService wagonEditService) {
        this.wagonService = wagonService;
        this.responsibleService = responsibleService;
        this.wagonEntryService = wagonEntryService;
        this.wagonEditService = wagonEditService;
    }

    /** Сменить состояние вагона вручную (Ф-ВАГ-02): администратор, с комментарием. */
    @PostMapping("/wagons/{id}/status")
    public WagonRow changeStatus(@PathVariable long id, @RequestBody WagonEditService.StatusBody body)
            throws Exception {
        return wagonEditService.changeStatus(id, body);
    }

    /** Сменить расположение вагона вручную (Ф-ВАГ-03): администратор, с комментарием и ответственным. */
    @PostMapping("/wagons/{id}/location")
    public WagonRow changeLocation(@PathVariable long id, @RequestBody WagonEditService.LocationBody body)
            throws Exception {
        return wagonEditService.changeLocation(id, body);
    }

    /** Сменить ответственного за вагон без акта (Ф-ВАГ-07): мастер депо станции вагона, администратор. */
    @PostMapping("/wagons/{id}/responsible")
    public WagonRow changeResponsible(@PathVariable long id, @RequestBody WagonEditService.ResponsibleBody body)
            throws Exception {
        return wagonEditService.changeResponsible(id, body);
    }

    /** Ввести вагон в эксплуатацию (Ф-ВАГ-01). */
    @PostMapping("/wagons")
    public WagonRow create(@RequestBody WagonEntryService.WagonBody body) throws Exception {
        return wagonEntryService.create(body);
    }

    /** Исправить данные вагона (Ф-ВАГ-08): администратор. */
    @PutMapping("/wagons/{id}")
    public WagonRow update(@PathVariable long id, @RequestBody WagonEntryService.WagonBody body) throws Exception {
        return wagonEntryService.update(id, body);
    }

    /**
     * Кого можно назначить ответственным за вагон: сотрудники депо станции station, бригада рейса trip и бригады
     * рейсов поезда вагонов wagons; без station у рейса - сотрудники депо станции прибытия.
     */
    @GetMapping("/wagons/responsibles")
    public List<ResponsibleCandidate> responsibles(@RequestParam(required = false) Long station,
                                                   @RequestParam(required = false) Long trip,
                                                   @RequestParam(required = false) List<Long> wagons)
            throws Exception {
        return responsibleService.candidates(station, trip, wagons);
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


    /** Удалить, если на запись никто не ссылается: иначе 409 с причиной. */
    @DeleteMapping("/wagons/{id}")
    public void delete(@PathVariable long id) throws Exception {
        wagonEntryService.delete(id);
    }
}
