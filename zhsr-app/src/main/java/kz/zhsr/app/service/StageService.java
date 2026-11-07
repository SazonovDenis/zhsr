package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.repository.Db;
import kz.kis.kisdbtools.repository.DbRecMapper;
import kz.zhsr.app.dto.route.MapPoint;
import kz.zhsr.app.dto.route.StageLine;
import kz.zhsr.app.dto.route.StageLines;
import kz.zhsr.app.dto.route.StationPoint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Перегоны на карте: точки станций и линии перегонов (stage, stage_point) для рисунка пути.
 */
@Service
public class StageService {

    private final Db db;

    public StageService(Db db) {
        this.db = db;
    }

    /**
     * Рисунок пути по станциям (GET /stages/lines): точки станций и линии перегонов между соседними станциями
     * списка. Соседние станции соединены перегоном напрямую - его линия (из нескольких перегонов между ними - самый
     * короткий); иначе - линии перегонов кратчайшего по километражу пути между ними: так рисуются пары остановок,
     * для которых станции следования ещё не построены (маршрут правится в редакторе). Пути нет - линий у пары нет.
     * Линии идут от предыдущей станции к следующей.
     *
     * @param stations станции по порядку (станции маршрута: остановки и станции следования)
     */
    public StageLines lines(List<Long> stations) throws Exception {
        if (stations == null || stations.isEmpty()) {
            return new StageLines(List.of(), List.of());
        }

        // граф перегонов: по станции - перегоны, которые от неё отходят
        Map<Long, List<DbRec>> graph = new HashMap<>();
        for (DbRec stage : db.loadList("stage", null)) {
            graph.computeIfAbsent(stage.getLong("from_station"), k -> new ArrayList<>()).add(stage);
            graph.computeIfAbsent(stage.getLong("to_station"), k -> new ArrayList<>()).add(stage);
        }

        // путь по перегонам для каждой пары соседних станций
        List<List<DbRec>> paths = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (int i = 1; i < stations.size(); i++) {
            List<DbRec> path = path(graph, stations.get(i - 1), stations.get(i));
            paths.add(path);
            path.forEach(stage -> used.add(stage.getLong("id")));
        }

        Map<Long, List<MapPoint>> points = stagePoints(used);
        List<StageLine> lines = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            long at = stations.get(i);
            for (DbRec stage : paths.get(i)) {
                long next = other(stage, at);
                List<MapPoint> line = points.getOrDefault(stage.getLong("id"), List.of());
                // точки перегона идут от from_station; путь идёт в обратную сторону - линию разворачиваем
                if (stage.getLong("from_station") != at) {
                    line = line.reversed();
                }
                lines.add(new StageLine(i, stage.getLong("id"), at, next, line));
                at = next;
            }
        }

        return new StageLines(stationPoints(new HashSet<>(stations)), lines);
    }

    /**
     * Кратчайший по километражу путь между станциями: перегоны по порядку от from к to. Станции соединены перегоном
     * напрямую - самый короткий из таких перегонов; та же станция или пути нет - пустой список.
     */
    private static List<DbRec> path(Map<Long, List<DbRec>> graph, long from, long to) {
        if (from == to) {
            return List.of();
        }
        DbRec direct = null;
        for (DbRec stage : graph.getOrDefault(from, List.of())) {
            if (other(stage, from) == to && (direct == null || stage.getInt("km") < direct.getInt("km"))) {
                direct = stage;
            }
        }
        if (direct != null) {
            return List.of(direct);
        }

        // Дейкстра: расстояние до станции и перегон, которым в неё пришли
        Map<Long, Integer> dist = new HashMap<>(Map.of(from, 0));
        Map<Long, DbRec> via = new HashMap<>();
        PriorityQueue<long[]> queue = new PriorityQueue<>(Comparator.comparingLong(e -> e[1]));
        queue.add(new long[]{from, 0});
        while (!queue.isEmpty()) {
            long[] e = queue.poll();
            long station = e[0];
            if (e[1] > dist.get(station)) {
                continue;
            }
            if (station == to) {
                break;
            }
            for (DbRec stage : graph.getOrDefault(station, List.of())) {
                long next = other(stage, station);
                int d = dist.get(station) + stage.getInt("km");
                Integer known = dist.get(next);
                if (known == null || d < known) {
                    dist.put(next, d);
                    via.put(next, stage);
                    queue.add(new long[]{next, d});
                }
            }
        }
        if (!via.containsKey(to)) {
            return List.of();
        }
        List<DbRec> res = new ArrayList<>();
        for (long station = to; station != from; ) {
            DbRec stage = via.get(station);
            res.add(stage);
            station = other(stage, station);
        }
        return res.reversed();
    }

    /** Другая станция перегона. */
    private static long other(DbRec stage, long station) {
        long a = stage.getLong("from_station");
        return a == station ? stage.getLong("to_station") : a;
    }

    /**
     * Точки станций: station.stage_point; у станции без точки записи нет.
     */
    private List<StationPoint> stationPoints(Collection<Long> stations) throws Exception {
        Map<Long, Long> pointOf = new HashMap<>();
        for (DbRec station : db.loadList("station", Map.of("id", stations))) {
            Long point = station.getLong("stage_point", null);
            if (point != null) {
                pointOf.put(station.getLong("id"), point);
            }
        }
        if (pointOf.isEmpty()) {
            return List.of();
        }
        Map<Long, MapPoint> points = new HashMap<>();
        for (DbRec p : db.loadList("stage_point", Map.of("id", new HashSet<>(pointOf.values())))) {
            points.put(p.getLong("id"), DbRecMapper.toObject(p, MapPoint.class));
        }
        List<StationPoint> res = new ArrayList<>();
        for (Map.Entry<Long, Long> e : pointOf.entrySet()) {
            MapPoint point = points.get(e.getValue());
            if (point != null) {
                res.add(new StationPoint(e.getKey(), point));
            }
        }
        return res;
    }

    /**
     * Точки линий перегонов по порядку seq.
     *
     * @return по id перегона - его точки
     */
    private Map<Long, List<MapPoint>> stagePoints(Collection<Long> stages) throws Exception {
        if (stages.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<DbRec>> recs = UtDb.collectGroupBy_records(
                db.loadList("stage_point", Map.of("stage", new HashSet<>(stages))), "stage");
        Map<Long, List<MapPoint>> res = new HashMap<>();
        for (Map.Entry<Long, List<DbRec>> e : recs.entrySet()) {
            res.put(e.getKey(), e.getValue().stream()
                    .sorted(Comparator.comparingInt(p -> p.getInt("seq")))
                    .map(p -> DbRecMapper.toObject(p, MapPoint.class))
                    .toList());
        }
        return res;
    }

}
