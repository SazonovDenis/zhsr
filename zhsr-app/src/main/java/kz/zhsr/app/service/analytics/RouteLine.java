package kz.zhsr.app.service.analytics;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtCnv;
import kz.zhsr.app.dto.route.MapPoint;
import kz.zhsr.app.dto.route.StageLine;
import kz.zhsr.app.dto.route.StageLines;

import java.util.ArrayList;
import java.util.List;

/**
 * Линия маршрута: ломаная с километрами от начала, станции на ней и расписание. Километр однозначен и там, где маршрут
 * возвращается тем же путём.
 */
final class RouteLine {

    private static final double KM_PER_DEGREE = 111.2;

    /** Найденное дальше по линии место заменяет найденное раньше, только если оно ближе к точке на столько км. */
    private static final double BETTER_KM = 0.3;

    /** Поезд не дальше стольких км от станции считается стоящим на ней. */
    private static final double STATION_KM = 0.5;

    private final List<double[]> points = new ArrayList<>();
    private final double[] stationKm;
    private final double[] arrival;
    private final double[] departure;

    /**
     * @param lines    рисунок пути по станциям маршрута
     * @param stations станции маршрута по порядку (route_station), не меньше одной
     */
    RouteLine(StageLines lines, List<DbRec> stations) {
        int count = stations.size();
        stationKm = new double[count];
        arrival = new double[count];
        departure = new double[count];

        double start = minute(stations.getFirst(), "departure_min", "arrival_min");
        for (int i = 0; i < count; i++) {
            arrival[i] = minute(stations.get(i), "arrival_min", "departure_min") - start;
            departure[i] = minute(stations.get(i), "departure_min", "arrival_min") - start;
        }

        for (StageLine line : lines.lines()) {
            for (MapPoint p : line.points()) {
                add(p.getX(), p.getY());
            }
            stationKm[line.index() + 1] = length();
        }
        // у пары станций без пути по перегонам линии нет: станция - там же, где предыдущая
        for (int i = 1; i < count; i++) {
            stationKm[i] = Math.max(stationKm[i], stationKm[i - 1]);
        }
    }

    private static double minute(DbRec station, String field, String other) {
        Long value = station.getLong(field);
        return value != null ? value : UtCnv.toLong(station.get(other));
    }

    private void add(double x, double y) {
        double km = 0;
        if (!points.isEmpty()) {
            double[] last = points.getLast();
            double step = distance(last[0], last[1], x, y);
            if (step == 0) {
                return;
            }
            km = last[2] + step;
        }
        points.add(new double[]{x, y, km});
    }

    private static double distance(double x1, double y1, double x2, double y2) {
        double k = Math.cos(Math.toRadians((y1 + y2) / 2));
        return KM_PER_DEGREE * Math.hypot((x1 - x2) * k, y1 - y2);
    }

    /** Точки линии: долгота, широта, километр. */
    List<double[]> points() {
        return points;
    }

    double length() {
        return points.isEmpty() ? 0 : points.getLast()[2];
    }

    /**
     * Километр места линии, ближайшего к точке, на участке [from, to]. Из равно близких мест (маршрут идёт обратно тем
     * же путём) - первое по ходу. Линии нет - from.
     */
    double match(double x, double y, double from, double to) {
        double best = from;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 1; i < points.size(); i++) {
            double[] a = points.get(i - 1);
            double[] b = points.get(i);
            if (b[2] < from) {
                continue;
            }
            if (a[2] > to) {
                break;
            }
            double k = Math.cos(Math.toRadians(a[1]));
            double dx = (b[0] - a[0]) * k;
            double dy = b[1] - a[1];
            double part = ((x - a[0]) * k * dx + (y - a[1]) * dy) / (dx * dx + dy * dy);
            part = Math.clamp(part, 0, 1);
            double km = Math.clamp(a[2] + (b[2] - a[2]) * part, from, to);
            part = (km - a[2]) / (b[2] - a[2]);
            double d = distance(x, y, a[0] + (b[0] - a[0]) * part, a[1] + (b[1] - a[1]) * part);
            boolean near = km - best <= 1;
            if (d < bestDistance - (near ? 0 : BETTER_KM)) {
                best = km;
                bestDistance = d;
            }
        }
        return best;
    }

    /**
     * Отклонение от расписания, мин: больше нуля - отставание.
     *
     * @param km     место поезда на линии
     * @param minute минут от планового отправления
     */
    double delay(double km, double minute) {
        int last = stationKm.length - 1;
        int i = 0;
        while (i < last && stationKm[i + 1] <= km) {
            i++;
        }
        if (i < last && stationKm[i + 1] - km <= STATION_KM && departure[i + 1] > arrival[i + 1]) {
            return delayAt(i + 1, minute);
        }
        if (i == last || km - stationKm[i] <= STATION_KM) {
            return delayAt(i, minute);
        }
        double part = (km - stationKm[i]) / (stationKm[i + 1] - stationKm[i]);
        return minute - (departure[i] + (arrival[i + 1] - departure[i]) * part);
    }

    private double delayAt(int station, double minute) {
        if (minute < arrival[station]) {
            return minute - arrival[station];
        }
        return Math.max(0, minute - departure[station]);
    }
}
