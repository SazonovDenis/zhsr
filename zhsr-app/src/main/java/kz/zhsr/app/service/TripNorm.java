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
 * проводника. Проводник с двумя несоседними вагонами или больше чем с двумя вагонами в норму не засчитывается. Показ нормы - zhsr-web, crewNorm.ts.
 */
public final class TripNorm {

    private TripNorm() {
    }

    /**
     * @return номера вагонов, у которых норма не выполнена, по порядку состава: пассажирские и любые вагоны проводника,
     * нарушившего правило «один вагон или два соседних»
     */
    public static List<String> notOk(List<TripCrew.Wagon> composition, List<TripCrew.Member> crew) {
        // норма - по вагонам, которые едут в поезде
        List<TripCrew.Wagon> wagons = composition.stream().filter(TripCrew.Wagon::isRiding).toList();
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
            List<TripCrew.Member> serving = conductors.stream().filter(m -> m.getWagons().contains(w.getNumber())).toList();
            boolean needSeniors = w.isPassenger() && (i == 0 || i == last) && serving.stream().filter(TripCrew.Member::isSenior).count() < 2;
            boolean notAdjacent = serving.stream().anyMatch(m -> !adjacent(m, order));
            if (!w.isPassenger()) {
                if (notAdjacent) {
                    res.add(w.getNumber());
                }
                continue;
            }
            if (!ok.contains(w.getNumber()) || needSeniors || notAdjacent) {
                res.add(w.getNumber());
            }
        }
        return res;
    }

    private static boolean adjacent(TripCrew.Member m, List<String> order) {
        return m.getWagons().size() < 2 || m.getWagons().size() == 2
                && Math.abs(order.indexOf(m.getWagons().get(0)) - order.indexOf(m.getWagons().get(1))) == 1;
    }

}
