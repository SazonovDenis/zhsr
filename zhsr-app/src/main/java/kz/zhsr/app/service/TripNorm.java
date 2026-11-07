package kz.zhsr.app.service;

import kz.zhsr.app.constants.CrewDuty;
import kz.zhsr.app.dto.trip.TripCrew;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Норма проводников рейса (Ф-РЕЙ-06): 2 проводника на пассажирский вагон или 3 на два соседних вагона; один проводник
 * обслуживает 1 вагон или 2 соседних; в хвостовые вагоны (первый и последний вагоны состава) - по 2 старших
 * проводника. Вагоны проводника, ставшие несоседними, в норму не засчитываются. Показ нормы - zhsr-web, crewNorm.ts.
 */
final class TripNorm {

    private TripNorm() {
    }

    /**
     * @return номера пассажирских вагонов, у которых норма не выполнена, по порядку состава
     */
    static List<String> notOk(List<TripCrew.Wagon> composition, List<TripCrew.Member> crew) {
        // отцепленные вагоны в составе не едут
        List<TripCrew.Wagon> wagons = composition.stream().filter(w -> !w.isDetached()).toList();
        List<String> order = wagons.stream().map(TripCrew.Wagon::getNumber).toList();
        List<TripCrew.Member> conductors = crew.stream().filter(m -> m.getCrewDutyType() == CrewDuty.CONDUCTOR).toList();

        Set<String> ok = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (TripCrew.Wagon w : wagons) {
            if (!w.isPassenger() || !seen.add(w.getNumber())) {
                continue;
            }
            // вагон и соседний, который обслуживает общий проводник, считаются вместе: 3 проводника на два вагона
            List<String> group = conductors.stream()
                    .filter(m -> m.getWagons().size() == 2 && m.getWagons().contains(w.getNumber()) && adjacent(m, order))
                    .findFirst().map(TripCrew.Member::getWagons).orElse(List.of(w.getNumber()));
            seen.addAll(group);
            long people = conductors.stream()
                    .filter(m -> adjacent(m, order) && m.getWagons().stream().anyMatch(group::contains))
                    .count();
            if (people >= (group.size() == 2 ? 3 : 2)) {
                ok.addAll(group);
            }
        }

        List<String> res = new ArrayList<>();
        int last = wagons.size() - 1;
        for (int i = 0; i <= last; i++) {
            TripCrew.Wagon w = wagons.get(i);
            if (!w.isPassenger()) {
                continue;
            }
            List<TripCrew.Member> serving = conductors.stream().filter(m -> m.getWagons().contains(w.getNumber())).toList();
            boolean needSeniors = (i == 0 || i == last) && serving.stream().filter(TripCrew.Member::isSenior).count() < 2;
            boolean notAdjacent = serving.stream().anyMatch(m -> !adjacent(m, order));
            if (!ok.contains(w.getNumber()) || needSeniors || notAdjacent) {
                res.add(w.getNumber());
            }
        }
        return res;
    }

    private static boolean adjacent(TripCrew.Member m, List<String> order) {
        return m.getWagons().size() < 2
                || Math.abs(order.indexOf(m.getWagons().get(0)) - order.indexOf(m.getWagons().get(1))) == 1;
    }

}
