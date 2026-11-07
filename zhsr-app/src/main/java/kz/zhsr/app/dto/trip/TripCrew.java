package kz.zhsr.app.dto.trip;

import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Бригада рейса: рейс, состав по порядку, сотрудники бригады и акты отцепки и прицепки.
 */
@Getter
@Setter
public class TripCrew {
    private Trip trip;
    private List<Wagon> wagons = List.of();
    private List<Member> crew = List.of();
    /** акты, новые первыми */
    private List<Act> acts = List.of();

    /**
     * Рейс.
     */
    @Getter
    @Setter
    public static class Trip {
        private long id;
        /** маршрут (route) */
        private long route;
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
    }

    /**
     * Вагон состава.
     */
    @Getter
    @Setter
    public static class Wagon {
        private String number;
        private long wagonType;
        /** пассажирский вагон: норма проводников требуется только для пассажирских вагонов */
        private boolean passenger;
        private long locationType;
        /** место расположения по виду: станция, депо, парк отстоя или рейс */
        private long place;
        /** рейс, в составе которого вагон сейчас; null - вагон не в рейсе */
        private TripRef trip;
        /** вагон отцеплен актом и в составе не едет */
        private boolean detached;
    }

    /**
     * Акт отцепки или прицепки.
     */
    @Getter
    @Setter
    public static class Act {
        private long id;
        private String number;
        private long couplingType;
        /** станция остановки */
        private long station;
        /** номера вагонов */
        private List<String> wagons = List.of();
        private OffsetDateTime actDt;
        private long actStatusType;
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
