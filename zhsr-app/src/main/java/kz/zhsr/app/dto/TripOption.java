package kz.zhsr.app.dto;

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
    /** название маршрута */
    private String name;
    /** вид маршрута (константы RouteKind) */
    private int routeKind;
    /** время в пути, мин (route.duration) */
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
