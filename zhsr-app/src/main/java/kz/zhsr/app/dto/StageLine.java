package kz.zhsr.app.dto;

import java.util.List;

/**
 * Линия перегона на пути между соседними станциями списка. Соседние станции соединены перегоном напрямую - одна линия;
 * иначе - линии перегонов кратчайшего пути между ними, по порядку.
 *
 * @param index       номер пары соседних станций: линия лежит на пути от stations[index] к stations[index + 1]
 * @param stage       перегон (stage)
 * @param fromStation станция перегона, от которой идёт линия (по ходу пути)
 * @param toStation   станция перегона, к которой идёт линия
 * @param points      точки линии по порядку от fromStation к toStation; первая и последняя - сами станции
 */
public record StageLine(int index, long stage, long fromStation, long toStation, List<MapPoint> points) {
    public StageLine {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
