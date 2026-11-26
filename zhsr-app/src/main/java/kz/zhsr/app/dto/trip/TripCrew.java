package kz.zhsr.app.dto.trip;

import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.train.TrainChange;
import kz.zhsr.app.dto.train.TrainWagon;
import kz.zhsr.app.dto.train.WagonProblem;
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
    /** вагоны поезда рейса на время рейса: до отправления - ожидаемый состав, после - факт; по позиции */
    private List<Wagon> wagons = List.of();
    /** неготовность вагонов рейса - тот же расчёт, что в готовности рейса (TripReadiness); у рейса в статусе
     * «выполнен» и «отменён» пусто */
    private List<WagonProblem> problems = List.of();
    private List<Member> crew = List.of();
    /** прицепки и отцепки рейса: факты и ожидающие планы, по времени */
    private List<TrainChange> changes = List.of();
    /** ожидающие планы, которые надо отметить или отменить до отправления: на станции отправления рейса и вне рейса
     * не позже отправления */
    private List<TrainChange> pending = List.of();
    /** отменённые планы рейса, по времени: их можно вернуть */
    private List<TrainChange> cancelled = List.of();
    /** отработанные (подтверждённые) планы рейса, по времени */
    private List<TrainChange> done = List.of();
    /** время последнего факта прицепки или отцепки поезда рейса: удалить можно только факт этого времени; null - фактов
     * нет или поезд не назначен */
    private OffsetDateTime lastFactDt;

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
    }

    /**
     * Вагон поезда рейса: до отправления - по ожидаемому составу, после - по факту.
     */
    @Getter
    @Setter
    public static class Wagon extends TrainWagon {
        /** позиция в поезде: на отправлении (у прицепляемого в пути - при прицепке) */
        private int seq;
        /** вагон едет в поезде рейса: в рейсе в статусе «план» - на отправлении, «выполняется» - сейчас,
         * «выполнен» - на прибытии; иначе отцеплен или прицепляется в пути */
        private boolean riding;
        /** прицепки и отцепки вагона в этом рейсе: факты и ожидающие планы, по времени - вычисляемое */
        private List<TrainChange> changes = List.of();
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
        /** номера вагонов, за которые сотрудник отвечает на рейс (журнал ответственных: до отправления - план на рейс,
         * в пути - факт, после прибытия - факт на момент прибытия), у вагонов, которые едут в поезде - вычисляемое */
        private List<String> wagons = List.of();
    }
}
