package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.ref.WagonRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Прицепка или отцепка вагона - запись журнала train_wagon_change: план или факт.
 */
@Getter
@Setter
public class TrainChange {
    private long id;
    private TrainRef train;
    private WagonRef wagon;
    /** слой: константы TrainWagonKind */
    private int kind;
    /** статус плана: константы TrainWagonPlanStatus; у факта null */
    private Integer planStatus;
    /** вид: константы TrainWagonChangeType */
    private int changeType;
    /** время: у плана плановое, у факта фактическое */
    private OffsetDateTime dt;
    /** позиция, на которую прицепляют вагон; у отцепки null */
    private Integer seq;
    private long station;
    /** рейс прицепки или отцепки; null - вне рейса */
    private TripRef trip;
    /** остановка маршрута рейса (route_station); null - вне рейса */
    private Long routeStation;
    /** у факта: отработанный им план (train_wagon_change.id); null - факт без плана */
    private Long plan;
    /** кто внёс */
    private UserRef usr;
    /** основания, примечания */
    private String comment;
    /** у факта по плану: плановое время - вычисляемое */
    private OffsetDateTime planDt;
    /** у факта по плану: плановая станция - вычисляемое */
    private Long planStation;
    /** у отработанного плана: факт, который его отработал (train_wagon_change.id); иначе null - вычисляемое */
    private Long fact;
    /** у плана: плановое время прошло, план не отмечен - вычисляемое */
    private boolean overdue;
    /** текущий сотрудник может отметить план выполненным - вычисляемое */
    private boolean canMark;
    /** текущий сотрудник может отменить план - вычисляемое */
    private boolean canCancel;
    /** текущий сотрудник может удалить ошибочный факт (если факт последний у поезда) - вычисляемое */
    private boolean canUndo;
    /** у отменённого плана: текущий сотрудник может вернуть план - вычисляемое */
    private boolean canRestore;
    /** текущий сотрудник может исправить запись: ожидающий план - целиком, факт - основания - вычисляемое */
    private boolean canEdit;
}
