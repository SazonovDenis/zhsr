package kz.zhsr.app.constants;

/**
 * Состояние маршрута и поезда в фильтре списка: действует или нет.
 */
public final class ActiveState {

    /**
     * Действует.
     */
    public static final int ACTIVE = 1;

    /**
     * Не действует: маршрут завершён, поезд расформирован.
     */
    public static final int CLOSED = 2;

    private ActiveState() {
    }

}
