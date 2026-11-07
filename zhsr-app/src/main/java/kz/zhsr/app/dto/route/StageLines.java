package kz.zhsr.app.dto.route;

import java.util.List;

/**
 * Рисунок пути на карте (GET /stages/lines): точки станций и линии перегонов между соседними станциями.
 *
 * @param stations станции, у которых есть точка на карте; у станции без перегонов записи нет
 * @param lines    линии перегонов по порядку станций; у пары соседних станций, между которыми нет пути по перегонам,
 *                 линий нет
 */
public record StageLines(List<StationPoint> stations, List<StageLine> lines) {
    public StageLines {
        stations = stations == null ? List.of() : List.copyOf(stations);
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
