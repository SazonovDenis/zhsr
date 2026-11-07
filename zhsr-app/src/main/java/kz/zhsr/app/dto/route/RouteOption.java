package kz.zhsr.app.dto.route;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Маршрут как вариант выбора (комбо «Маршрут»): маршрут и то, что строке варианта нужно от его станций. Строка таблицы
 * маршрутов ({@link RouteRow}) - вариант выбора и поля таблицы.
 */
@Getter
@Setter
public class RouteOption {
    private long id;
    /** номер поезда */
    private String trainNumber;
    private String name;
    /** вид маршрута (константы RouteKind) */
    private int routeKind;
    /** время в пути, мин; вводит пользователь */
    private int duration;
    /** длина маршрута, км: километраж последней станции маршрута */
    private int km;
    /** остановки маршрута по порядку - id станций (station); станции следования не входят */
    private List<Long> stops = List.of();
}
