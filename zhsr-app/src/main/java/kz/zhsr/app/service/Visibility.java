package kz.zhsr.app.service;

import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.constants.RoleType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Видимость данных по ролям сотрудника: что сотрудник видит в списках вагонов, рейсов, маршрутов и неисправностей.
 * <p>
 * Настройка - карта {@link #BY_ROLE} «роль - область видимости». Сотрудник с несколькими ролями видит
 * объединение того, что открывает каждая роль. Роль, которой нет в карте, ничего не открывает.
 * <p>
 * Фильтр по депо приходит от фронта всегда как id депо или пусто. Роль с {@link Scope#OWN_DEPOT} подменяет его депо
 * сотрудника; роль с {@link Scope#ANY_DEPOT} оставляет как пришло (пусто - все депо).
 */
@Service
public class Visibility {

    /**
     * Область видимости роли.
     */
    public enum Scope {
        /**
         * Любое депо: фильтр по депо - как выбрал сотрудник, пусто - все депо.
         */
        ANY_DEPOT,
        /**
         * Только депо сотрудника, принудительно; депо нет - роль ничего не открывает.
         */
        OWN_DEPOT,
        /**
         * Только текущий рейс сотрудника: рейс, его маршрут и все вагоны его состава; рейса нет - ничего.
         */
        OWN_TRIP,
        /**
         * Только вагоны, за которыми сотрудник закреплён в текущем рейсе, сам рейс и его маршрут;
         * рейса нет или вагон не закреплён - ничего.
         */
        OWN_WAGON
    }

    /**
     * Роль (role_type.id, константы RoleType) - область видимости. Правится здесь.
     */
    public static final Map<Long, Scope> BY_ROLE = Map.ofEntries(
            Map.entry(RoleType.ADMIN, Scope.ANY_DEPOT),
            Map.entry(RoleType.MANAGER, Scope.ANY_DEPOT),
            Map.entry(RoleType.WAGON_PLANNER, Scope.ANY_DEPOT),
            Map.entry(RoleType.CREW_PLANNER, Scope.ANY_DEPOT),
            Map.entry(RoleType.INSTRUCTOR, Scope.ANY_DEPOT),
            Map.entry(RoleType.SCHEDULER, Scope.ANY_DEPOT),
            Map.entry(RoleType.MEDIC, Scope.ANY_DEPOT),
            Map.entry(RoleType.OPERATOR, Scope.ANY_DEPOT),
            Map.entry(RoleType.MASTER, Scope.OWN_DEPOT),
            Map.entry(RoleType.RECEIVER, Scope.OWN_DEPOT),
            Map.entry(RoleType.EQUIPPER, Scope.OWN_DEPOT),
            Map.entry(RoleType.CLEANING, Scope.OWN_DEPOT),
            Map.entry(RoleType.LNP, Scope.OWN_TRIP),
            Map.entry(RoleType.PEM, Scope.OWN_TRIP),
            Map.entry(RoleType.CONDUCTOR, Scope.OWN_WAGON)
    );

    /**
     * Что открыто сотруднику. Пустые части ничего не открывают; всё пусто - сотрудник не видит ничего.
     *
     * @param byDepot      открыты данные по депо
     * @param depot        депо для фильтра, если byDepot; null - все депо
     * @param trip         открыт текущий рейс сотрудника и его маршрут; null - нет
     * @param allTripWagons открыты все вагоны состава рейса trip
     * @param wagons       открыты эти вагоны; пусто - нет
     * @param trips        открытые рейсы сотрудника для списка рейсов: текущий и соседний (предыдущий или следующий),
     *                     если открыт текущий; пусто - нет
     */
    public record Access(boolean byDepot, Long depot, Long trip, boolean allTripWagons, List<Long> wagons,
                         List<Long> trips) {
        public Access {
            wagons = wagons == null ? List.of() : List.copyOf(wagons);
            trips = trips == null ? List.of() : List.copyOf(trips);
        }
    }

    /**
     * @param user           сотрудник
     * @param requestedDepot депо из фильтра фронта; null - не указано
     */
    public Access access(CurrentUser user, Long requestedDepot) {
        boolean anyDepot = false;
        boolean ownDepot = false;
        boolean ownTrip = false;
        boolean ownWagon = false;
        for (CurrentUser.Role role : user.roles()) {
            Scope scope = BY_ROLE.get(role.id());
            if (scope == null) {
                continue;
            }
            switch (scope) {
                case ANY_DEPOT -> anyDepot = true;
                case OWN_DEPOT -> ownDepot = true;
                case OWN_TRIP -> ownTrip = true;
                case OWN_WAGON -> ownWagon = true;
            }
        }

        boolean byDepot = anyDepot || (ownDepot && user.depot() != null);
        Long depot = anyDepot ? requestedDepot : user.depot();

        CurrentUser.Trip trip = user.trip();
        List<Long> wagons = ownWagon && trip != null ? trip.wagons() : List.of();
        // проводник без закреплённого вагона не видит и рейса
        Long tripId = trip != null && (ownTrip || !wagons.isEmpty()) ? trip.id() : null;

        // соседний рейс (предыдущий или следующий) виден так же, как текущий: его можно открыть на телефоне
        List<Long> trips = new ArrayList<>();
        if (tripId != null) {
            trips.add(tripId);
            if (user.otherTrip() != null) {
                trips.add(user.otherTrip().id());
            }
        }
        return new Access(byDepot, byDepot ? depot : null, tripId, ownTrip && tripId != null, wagons, trips);
    }

}
