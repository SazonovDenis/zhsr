package kz.zhsr.app.controller;

import kz.zhsr.app.dto.route.StageLines;
import kz.zhsr.app.service.StageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Перегоны на карте. Станции - повтором параметра: {@code stations=1&stations=2}.
 */
@RestController
public class StageController {

    private final StageService stageService;

    public StageController(StageService stageService) {
        this.stageService = stageService;
    }

    @GetMapping("/stages/lines")
    public StageLines lines(@RequestParam(required = false) List<Long> stations) throws Exception {
        return stageService.lines(stations);
    }

}
