package kz.zhsr.app.controller;

import kz.zhsr.app.dto.analytics.ExecutionRow;
import kz.zhsr.app.dto.analytics.GeoChart;
import kz.zhsr.app.dto.analytics.OvertimeChart;
import kz.zhsr.app.dto.analytics.RequestsChart;
import kz.zhsr.app.dto.analytics.UncheckedWagonRow;
import kz.zhsr.app.dto.analytics.WorkTimeRow;
import kz.zhsr.app.service.analytics.AnalyticsService;
import kz.zhsr.app.service.analytics.GeoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Данные диаграмм раздела «Аналитика».
 */
@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final GeoService geoService;

    public AnalyticsController(AnalyticsService analyticsService, GeoService geoService) {
        this.analyticsService = analyticsService;
        this.geoService = geoService;
    }

    @GetMapping("/analytics/overtime")
    public OvertimeChart overtime() throws Exception {
        return analyticsService.overtime();
    }

    @GetMapping("/analytics/unchecked-wagons")
    public List<UncheckedWagonRow> uncheckedWagons(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime at)
            throws Exception {
        return analyticsService.uncheckedWagons(at);
    }

    @GetMapping("/analytics/requests")
    public RequestsChart requests(@RequestParam(required = false) Long service) throws Exception {
        return analyticsService.requests(service);
    }

    @GetMapping("/analytics/execution")
    public List<ExecutionRow> execution() throws Exception {
        return analyticsService.execution();
    }

    @GetMapping("/analytics/work-time")
    public List<WorkTimeRow> workTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) throws Exception {
        return analyticsService.workTime(from, to);
    }

    @GetMapping("/analytics/geo")
    public GeoChart geo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "true") boolean points) throws Exception {
        return geoService.chart(from, to, points);
    }
}
