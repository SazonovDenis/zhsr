package kz.zhsr.app.dto.trip;

import kz.zhsr.app.dto.ref.TrainRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Рейс как вариант выбора (комбо «Рейс»): рейс и то, что строке варианта нужно от его маршрута. Строка таблицы
 * рейсов ({@link TripRow}) - вариант выбора и поля таблицы.
 */
@Getter
@Setter
public class TripOption {
    private long id;
    /** номер поезда маршрута */
    private String trainNumber;
    /** маршрут рейса (route) */
    private long route;
    /** поезд рейса; null - поезд не назначен */
    private TrainRef train;
    /** название маршрута */
    private String name;
    /** вид маршрута (константы RouteKind) */
    private int routeKind;
    /** время в пути, мин: по станциям маршрута */
    private int duration;
    /** состояние рейса (trip_status_type) */
    private long tripStatusType;
    /** плановое отправление */
    private OffsetDateTime plannedDt;
    /** длина маршрута, км: километраж последней станции маршрута */
    private int km;
    /** остановки маршрута по порядку - id станций (station); станции следования не входят */
    private List<Long> stops = List.of();
}
