package kz.zhsr.app.controller;

import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.staff.StaffFilter;
import kz.zhsr.app.dto.staff.StaffRow;
import kz.zhsr.app.service.StaffService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Сотрудники: список, перемещение в другое депо, смена категории проводника.
 */
@RestController
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping("/staff")
    public List<StaffRow> list(StaffFilter filter, ListSort sort) throws Exception {
        return staffService.list(filter, sort);
    }

    @PostMapping("/staff/{id}/depot")
    public StaffRow moveDepot(@PathVariable long id, @RequestBody StaffService.PeriodBody body) throws Exception {
        return staffService.moveDepot(id, body);
    }

    @PostMapping("/staff/{id}/category")
    public StaffRow changeCategory(@PathVariable long id, @RequestBody StaffService.PeriodBody body)
            throws Exception {
        return staffService.changeCategory(id, body);
    }

}
