package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Маршрутная сеть (Ф-МАР-01): станции, перегоны (stage, в обе стороны) и узловые станции - станции, на которых есть
 * депо. Пути - кратчайшие по километражу перегонов. Сеть читается из БД на каждый вызов {@link #load()}: перегоны и
 * депо правит администрирование.
 */
@Service
public class RouteNetwork {

    /**
     * Сеть на момент загрузки.
     *
     * @param edges станция - соседние станции и длина перегона до них, км
     * @param hubs  узловые станции
     */
    public record Graph(Map<Long, Map<Long, Integer>> edges, Set<Long> hubs) {

        /**
         * Длина перегона между соседними станциями; null - перегона нет.
         */
        public Integer stage(long from, long to) {
            return edges.getOrDefault(from, Map.of()).get(to);
        }

        /**
         * Кратчайшие пути от станции до всех достижимых.
         */
        public Map<Long, Path> paths(long from) {
            Map<Long, Path> res = new HashMap<>();
            res.put(from, new Path(0, 0, List.of()));
            PriorityQueue<long[]> queue = new PriorityQueue<>(Comparator.comparingLong(a -> a[1]));
            queue.add(new long[]{from, 0});
            Map<Long, Long> prev = new HashMap<>();
            Set<Long> done = new HashSet<>();
            while (!queue.isEmpty()) {
                long[] top = queue.poll();
                long station = top[0];
                if (!done.add(station)) {
                    continue;
                }
                Path cur = res.get(station);
                for (Map.Entry<Long, Integer> e : edges.getOrDefault(station, Map.of()).entrySet()) {
                    long next = e.getKey();
                    int km = cur.km() + e.getValue();
                    Path known = res.get(next);
                    if (known == null || km < known.km()) {
                        res.put(next, new Path(km, cur.stages() + 1, null));
                        prev.put(next, station);
                        queue.add(new long[]{next, km});
                    }
                }
            }
            // станции следования каждого пути - по цепочке предыдущих станций
            Map<Long, Path> full = new HashMap<>();
            for (Map.Entry<Long, Path> e : res.entrySet()) {
                List<Long> via = new ArrayList<>();
                Long p = prev.get(e.getKey());
                while (p != null && p != from) {
                    via.add(p);
                    p = prev.get(p);
                }
                Collections.reverse(via);
                full.put(e.getKey(), new Path(e.getValue().km(), e.getValue().stages(), via));
            }
            return full;
        }
    }

    /**
     * Путь от станции.
     *
     * @param km     длина, км
     * @param stages число перегонов
     * @param via    станции следования по порядку, без начальной и конечной
     */
    public record Path(int km, int stages, List<Long> via) {
    }

    private final Db db;

    public RouteNetwork(Db db) {
        this.db = db;
    }

    /**
     * Сеть из БД.
     */
    public Graph load() throws Exception {
        Map<Long, Map<Long, Integer>> edges = new HashMap<>();
        for (DbRec s : db.loadList("stage", Map.of())) {
            long a = s.getLong("from_station");
            long b = s.getLong("to_station");
            int km = s.getInt("km");
            edges.computeIfAbsent(a, k -> new HashMap<>()).merge(b, km, Math::min);
            edges.computeIfAbsent(b, k -> new HashMap<>()).merge(a, km, Math::min);
        }
        Set<Long> hubs = new HashSet<>();
        for (DbRec d : db.loadList("depot", Map.of())) {
            Long station = d.getLong("station", null);
            if (station != null) {
                hubs.add(station);
            }
        }
        return new Graph(edges, hubs);
    }

}
