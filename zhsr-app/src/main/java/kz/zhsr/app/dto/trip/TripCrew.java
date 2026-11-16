package kz.zhsr.app.dto.trip;

import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.train.TrainChange;
import kz.zhsr.app.dto.train.TrainWagon;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Рейс, вагоны поезда рейса по порядку, бригада и прицепки и отцепки рейса.
 */
@Getter
@Setter
public class TripCrew {
    private Trip trip;
    /** вагоны поезда рейса на время рейса: по факту и по плану */
    private List<Wagon> wagons = List.of();
    private List<Member> crew = List.of();
    /** прицепки и отцепки рейса: плановые и выполненные, по времени */
    private List<TrainChange> changes = List.of();
    /** плановые прицепки и отцепки, которые надо отметить до отправления: на станции отправления и вне рейса раньше
     * отправления */
    private List<TrainChange> pending = List.of();

    /**
     * Рейс.
     */
    @Getter
    @Setter
    public static class Trip {
        private long id;
        /** маршрут (route) */
        private long route;
        /** поезд рейса; null - поезд не назначен */
        private TrainRef train;
        private String trainNumber;
        /** название маршрута */
        private String name;
        private long tripStatusType;
        private long transportType;
        /** время в пути, мин */
        private int duration;
        private OffsetDateTime plannedDt;
        /** null - рейс не отправлен */
        private OffsetDateTime departedDt;
        /** null - рейс не прибыл */
        private OffsetDateTime arrivedDt;
        /** когда отмечено отправление; null - не отмечено */
        private OffsetDateTime departureMarkedDt;
        /** когда отмечено прибытие; null - не отмечено */
        private OffsetDateTime arrivalMarkedDt;
        /** отправление: фактическое, если было, иначе плановое */
        private OffsetDateTime departure;
        /** плановое прибытие */
        private OffsetDateTime arrival;
        /** станция отправления; null - у маршрута нет остановок */
        private Long departureStation;
        /** станция прибытия; null - у маршрута нет остановок */
        private Long arrivalStation;
        /** остановки маршрута по порядку - id станций */
        private List<Long> stops = List.of();
        /** остановки маршрута по порядку - route_station.id, по порядку stops */
        private List<Long> routeStations = List.of();
        /** время стоянки на остановках по расписанию рейса, по порядку stops */
        private List<OffsetDateTime> stopTimes = List.of();
        /**
         * после прибытия назначен ответственный за вагоны (Ф-РЕЙ-04); false - не назначен, ответственные - проводники
         * вагонов
         */
        private boolean responsibleAssigned;
        /** ответственный за вагоны, назначенный после прибытия; null - «ответственный не назначен» или назначения нет */
        private UserRef responsibleUsr;
    }

    /**
     * Вагон поезда рейса.
     */
    @Getter
    @Setter
    public static class Wagon extends TrainWagon {
        /** вагон едет в поезде рейса: в рейсе в статусе «план» - на отправлении, иначе - сейчас; иначе отцеплен или
         * прицепляется в пути */
        private boolean riding;
    }

    /**
     * Сотрудник бригады.
     */
    @Getter
    @Setter
    public static class Member {
        private UserRef usr;
        private long crewDutyType;
        private boolean senior;
        /** номера закреплённых вагонов */
        private List<String> wagons = List.of();
    }
}
