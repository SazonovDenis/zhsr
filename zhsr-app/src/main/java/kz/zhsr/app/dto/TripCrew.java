package kz.zhsr.app.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Формирование рейса: рейс, состав по порядку и бригада.
 *
 * @param trip   рейс
 * @param wagons состав по порядку (trip_wagon.seq)
 * @param crew   бригада рейса
 * @param acts   акты отцепки и прицепки по рейсу, новые первыми
 */
public record TripCrew(Trip trip, List<Wagon> wagons, List<Member> crew, List<Act> acts) {

    public TripCrew {
        wagons = wagons == null ? List.of() : List.copyOf(wagons);
        crew = crew == null ? List.of() : List.copyOf(crew);
        acts = acts == null ? List.of() : List.copyOf(acts);
    }

    /**
     * Рейс.
     *
     * @param route            маршрут рейса (route)
     * @param trainNumber      номер поезда
     * @param name             название маршрута
     * @param tripStatusType   состояние рейса (trip_status_type)
     * @param transportType    вид перевозки маршрута (transport_type)
     * @param duration         время в пути, мин (route.duration)
     * @param plannedDt        плановое отправление
     * @param departedDt       фактическое отправление; null - рейс не отправлен
     * @param departure        отправление: фактическое, если было, иначе плановое
     * @param arrival          плановое прибытие: плановое отправление плюс время в пути маршрута (route.duration)
     * @param departureStation первая остановка маршрута (station); null - у маршрута нет остановок
     * @param arrivalStation   последняя остановка маршрута (station); null - у маршрута нет остановок
     * @param stops            остановки маршрута по порядку (station)
     */
    public record Trip(long id, long route, String trainNumber, String name, long tripStatusType, long transportType,
                       int duration, OffsetDateTime plannedDt, OffsetDateTime departedDt, OffsetDateTime departure,
                       OffsetDateTime arrival, Long departureStation, Long arrivalStation, List<Long> stops) {
        public Trip {
            stops = stops == null ? List.of() : List.copyOf(stops);
        }
    }

    /**
     * Вагон состава.
     *
     * @param wagonType    тип вагона (wagon_type)
     * @param passenger    пассажирский вагон: норма проводников требуется только для него
     * @param locationType вид расположения (location_type)
     * @param place        место расположения по виду: станция, депо, парк отстоя или рейс
     * @param trainNumber  номер поезда рейса, в составе которого вагон сейчас; null - вагон не в рейсе
     * @param tripName     маршрут рейса, в составе которого вагон сейчас; null - вагон не в рейсе
     * @param detached     вагон отцеплен актом и в составе не едет
     */
    public record Wagon(String number, long wagonType, boolean passenger, long locationType, long place,
                        String trainNumber, String tripName, boolean detached) {
    }

    /**
     * Акт отцепки или прицепки по рейсу.
     *
     * @param couplingType вид акта (coupling_type)
     * @param station станция остановки (station)
     * @param wagons  номера вагонов акта
     * @param createdDt когда составлен
     * @param actStatusType состояние акта (act_status_type)
     */
    public record Act(String number, long couplingType, long station, List<String> wagons, OffsetDateTime createdDt,
                      long actStatusType) {
        public Act {
            wagons = wagons == null ? List.of() : List.copyOf(wagons);
        }
    }

    /**
     * Сотрудник бригады.
     *
     * @param usr    usr.id
     * @param name   фамилия и инициалы
     * @param crewDutyType должность на рейсе (crew_duty_type)
     * @param senior категория «старший проводник»
     * @param wagons номера вагонов проводника: один или два соседних; пусто - проводник не распределён
     */
    public record Member(long usr, String name, long crewDutyType, boolean senior, List<String> wagons) {
        public Member {
            wagons = wagons == null ? List.of() : List.copyOf(wagons);
        }
    }

}
