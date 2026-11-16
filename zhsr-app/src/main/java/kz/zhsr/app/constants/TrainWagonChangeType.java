package kz.zhsr.app.constants;

/**
 * Вид изменения участия вагона в поезде: значения train_wagon_change.change_type.
 */
public final class TrainWagonChangeType {

    /**
     * Прицепка: вагон входит в поезд - на станции или в пути.
     */
    public static final int ATTACH = 1;

    /**
     * Отцепка: вагон выходит из поезда - на станции или в пути.
     */
    public static final int DETACH = 2;

    private TrainWagonChangeType() {
    }

}
