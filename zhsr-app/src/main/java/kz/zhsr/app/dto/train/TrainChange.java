package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.ref.WagonRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Запись журнала поезда, план или факт: прицепка или отцепка вагона (train_wagon_change) или смена порядка вагонов
 * (train_wagon_seq). Что за запись, говорит code; запись определяют code и id; plan и fact - записи той же таблицы.
 */
@Getter
@Setter
public class TrainChange {
    private long id;
    /** таблица записи: константы TrainJournalCode - вычисляемое */
    private String code;
    private TrainRef train;
    /** вагон прицепки или отцепки; у смены порядка null */
    private WagonRef wagon;
    /** смена порядка: новый порядок вагонов по позициям - вычисляемое; у прицепки и отцепки пусто */
    private List<TrainWagonSeqItem> trainWagonSeqItem = List.of();
    /** слой: константы TrainWagonKind */
    private int kind;
    /** статус плана: константы TrainWagonPlanStatus; у факта null */
    private Integer planStatus;
    /** вид: константы TrainWagonChangeType; у смены порядка null */
    private Integer changeType;
    /** время: у плана плановое, у факта фактическое */
    private OffsetDateTime dt;
    /** позиция, на которую прицепляют вагон; у отцепки и смены порядка null */
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
