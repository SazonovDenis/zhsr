package kz.zhsr.app.controller;

import kz.zhsr.app.dto.common.BuildInfo;
import kz.zhsr.app.service.BuildInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Сведения о сборке приложения для клиента: только вызов {@link BuildInfoService}.
 */
@RestController
@RequestMapping("/buildInfo")
public class BuildInfoController {

    private final BuildInfoService buildInfoService;

    public BuildInfoController(BuildInfoService buildInfoService) {
        this.buildInfoService = buildInfoService;
    }

    @GetMapping
    public BuildInfo get() {
        return buildInfoService.get();
    }

}
