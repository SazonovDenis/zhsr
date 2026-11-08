package kz.zhsr.app.dto.wagon;

import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Строка журнала вагона (Ф-ВАГ-05): одно событие - записи журналов расположения и состояния, которые открыл один
 * документ в одно время.
 */
@Getter
@Setter
public class WagonJournalRow {

    // основание записи - basisType

    /** ввод вагона: первая запись журнала без документа */
    public static final int BASIS_ENTRY = 1;
    /** ручная правка: запись без документа, не первая */
    public static final int BASIS_MANUAL = 2;
    /** акт приёма-передачи */
    public static final int BASIS_TRANSFER = 3;
    /** акт отцепки */
    public static final int BASIS_DETACH = 4;
    /** акт прицепки */
    public static final int BASIS_ATTACH = 5;
    /** отправление рейса */
    public static final int BASIS_DEPARTURE = 6;
    /** прибытие рейса */
    public static final int BASIS_ARRIVAL = 7;

    /** время события */
    private OffsetDateTime dbeg;
    /** расположение после события */
    private WagonPlace location;
    /** расположение до события; null - расположение событием не менялось или это первая запись */
    private WagonPlace prevLocation;
    /** событие открыло запись журнала расположения */
    private boolean locationChanged;
    /** состояние после события (wagon_status_type) */
    private long wagonStatusType;
    /** состояние до события; null - состояние событием не менялось или это первая запись */
    private Long prevWagonStatusType;
    /** событие открыло запись журнала состояния */
    private boolean statusChanged;
    /** основание - константы BASIS_* */
    private int basisType;
    /** номер акта - у актов приёма-передачи, отцепки и прицепки; иначе null */
    private String actNumber;
    /** рейс - у отправления, прибытия, отцепки и прицепки; иначе null */
    private TripRef basisTrip;
    /** кто провёл документ; null - документа нет */
    private UserRef byUsr;
    /** причина из документа (акт отцепки или прицепки); null - нет */
    private String comment;
}
