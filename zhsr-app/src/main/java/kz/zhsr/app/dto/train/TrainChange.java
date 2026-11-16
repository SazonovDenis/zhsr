package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Прицепка или отцепка вагона (train_wagon_change).
 */
@Getter
@Setter
public class TrainChange {
    private long id;
    /** участие вагона в поезде (train_wagon) */
    private long trainWagon;
    /** вид: константы TrainWagonChangeType */
    private int changeType;
    /** статус: константы TrainWagonChangeStatus */
    private int status;
    /** плановое время; null - не планировалось */
    private OffsetDateTime plannedDt;
    /** фактическое время; null - не выполнено */
    private OffsetDateTime factDt;
    private long station;
    /** рейс прицепки или отцепки; null - вне рейса */
    private TripRef trip;
    /** остановка маршрута рейса (route_station); null - вне рейса */
    private Long routeStation;
    /** кто внёс */
    private UserRef usr;
    /** основания, примечания */
    private String comment;
    /** номер вагона - вычисляемое */
    private String number;
    /** текущий сотрудник может отметить плановое изменение выполненным или отменить план - вычисляемое */
    private boolean canMark;
    /** текущий сотрудник может отменить выполненное изменение без истории - вычисляемое */
    private boolean canUndo;
}
