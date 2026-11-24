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
 * Перегоны на карте: точки станций и линии перегонов (stage, stage_point, station_point) для рисунка пути.
 */
@Service
public class StageService {

    private final Db db;

    public StageService(Db db) {
        this.db = db;
    }

    /**
     * Точки всех станций (GET /stages/stations); у станции без точки записи нет.
     */
    public List<StationPoint> stations() throws Exception {
        return db.loadList("station_point", Map.of()).stream()
                .map(rec -> new StationPoint(rec.getLong("station"), DbRecMapper.toObject(rec, MapPoint.class)))
                .toList();
    }

    /**
     * Рисунок пути по станциям (GET /stages/lines): точки станций и линии перегонов между соседними станциями
     * списка. Соседние станции соединены перегоном напрямую - его линия (из нескольких перегонов между ними - самый
     * короткий); иначе - линии перегонов кратчайшего по километражу пути между ними: так рисуются пары остановок,
     * для которых станции следования ещё не построены (маршрут правится в редакторе). Пути нет - линий у пары нет.
     * Линии идут от предыдущей станции к следующей.
     * <p>
     * У перегона нет точек - прямая между точками его станций. У станции нет и точки - прямая между ближайшими станциями
     * пути, у которых точка есть.
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

        // все станции рисунка: станции списка и промежуточные станции путей
        Set<Long> all = new HashSet<>(stations);
        paths.forEach(path -> path.forEach(stage -> {
            all.add(stage.getLong("from_station"));
            all.add(stage.getLong("to_station"));
        }));
        Map<Long, MapPoint> stationPoints = stationPoints(all);
        Map<Long, List<MapPoint>> points = stagePoints(used);

        List<StageLine> lines = new ArrayList<>();
        // перегоны подряд, которые рисуются только частью общей прямой
        List<Leg> gap = new ArrayList<>();
        MapPoint gapStart = null;
        for (int i = 0; i < paths.size(); i++) {
            long at = stations.get(i);
            if (paths.get(i).isEmpty() && at != stations.get(i + 1)) {
                // пути по перегонам нет: через этот разрыв прямая не проводится
                closeGap(lines, gap, null, null);
                gapStart = null;
                continue;
            }
            for (DbRec stage : paths.get(i)) {
                Leg leg = new Leg(i, stage, at, other(stage, at));
                List<MapPoint> line = line(leg, points, stationPoints);
                MapPoint from = line.isEmpty() ? stationPoints.get(leg.from()) : line.getFirst();
                MapPoint to = line.isEmpty() ? stationPoints.get(leg.to()) : line.getLast();
                if (from != null) {
                    closeGap(lines, gap, gapStart, from);
                    gapStart = from;
                }
                if (line.isEmpty()) {
                    gap.add(leg);
                } else {
                    lines.add(leg.line(line));
                }
                if (to != null) {
                    closeGap(lines, gap, gapStart, to);
                    gapStart = to;
                }
                at = leg.to();
            }
        }
        closeGap(lines, gap, null, null);

        // кружки - только станции списка
        List<StationPoint> res = new HashSet<>(stations).stream()
                .filter(stationPoints::containsKey)
                .map(station -> new StationPoint(station, stationPoints.get(station)))
                .toList();
        return new StageLines(res, lines);
    }

    /**
     * Перегон на пути.
     *
     * @param index номер пары соседних станций списка
     * @param from  станция перегона, от которой идёт путь
     * @param to    станция перегона, к которой идёт путь
     */
    private record Leg(int index, DbRec stage, long from, long to) {
        StageLine line(List<MapPoint> points) {
            return new StageLine(index, stage.getLong("id"), from, to, points);
        }
    }

    /**
     * Линия перегона по ходу пути: точки перегона, концы - в точках станций; точек перегона нет - прямая между точками
     * станций.
     *
     * @return пусто - точек перегона нет, и у одной из станций нет точки
     */
    private static List<MapPoint> line(Leg leg, Map<Long, List<MapPoint>> points, Map<Long, MapPoint> stationPoints) {
        MapPoint from = stationPoints.get(leg.from());
        MapPoint to = stationPoints.get(leg.to());
        List<MapPoint> line = points.getOrDefault(leg.stage().getLong("id"), List.of());
        if (line.size() < 2) {
            return from == null || to == null ? List.of() : List.of(from, to);
        }
        // точки перегона идут от from_station; путь идёт в обратную сторону - линию разворачиваем
        line = new ArrayList<>(leg.stage().getLong("from_station") == leg.from() ? line : line.reversed());
        if (from != null) {
            line.set(0, from);
        }
        if (to != null) {
            line.set(line.size() - 1, to);
        }
        return line;
    }

    /**
     * Нарисовать перегоны gap одной прямой от start до end: каждому перегону - её часть по километражу. Одного из концов
     * нет - перегоны без линий.
     */
    private static void closeGap(List<StageLine> lines, List<Leg> gap, MapPoint start, MapPoint end) {
        boolean drawn = start != null && end != null;
        double total = gap.stream().mapToInt(leg -> leg.stage().getInt("km")).sum();
        double km = 0;
        MapPoint last = start;
        for (Leg leg : gap) {
            km += leg.stage().getInt("km");
            MapPoint next = drawn ? between(start, end, km / total) : null;
            lines.add(leg.line(drawn ? List.of(last, next) : List.of()));
            last = next;
        }
        gap.clear();
    }

    /** Точка прямой от a до b на части part её длины; part = 1 - сама точка b. */
    private static MapPoint between(MapPoint a, MapPoint b, double part) {
        if (part >= 1) {
            return b;
        }
        return new MapPoint(a.getX() + (b.getX() - a.getX()) * part, a.getY() + (b.getY() - a.getY()) * part);
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
     * Точки станций (station_point).
     *
     * @return по id станции - её точка; у станции без точки записи нет
     */
    private Map<Long, MapPoint> stationPoints(Collection<Long> stations) throws Exception {
        Map<Long, MapPoint> res = new HashMap<>();
        for (DbRec rec : db.loadList("station_point", Map.of("station", stations))) {
            res.put(rec.getLong("station"), DbRecMapper.toObject(rec, MapPoint.class));
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
