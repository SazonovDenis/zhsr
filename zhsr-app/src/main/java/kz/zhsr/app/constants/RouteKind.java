package kz.zhsr.app.constants;

/**
 * Вид маршрута: значения route.route_kind.
 */
public final class RouteKind {

    /**
     * Регулярный маршрут: поезд по расписанию.
     */
    public static final int REGULAR = 1;

    /**
     * Разовый маршрут: поезд без расписания.
     */
    public static final int ONE_TIME = 2;

    private RouteKind() {
    }

}
