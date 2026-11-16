package kz.zhsr.app.dto.wagon;

import kz.zhsr.app.dto.ref.CheckTemplateRef;
import kz.zhsr.app.dto.ref.EquipTemplateRef;
import kz.zhsr.app.dto.ref.TripRef;
import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Строка журнала вагона (Ф-ВАГ-05): одно событие - записи журналов расположения, состояния и ответственного, которые
 * открыл один документ в одно время.
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
    /** отцепка в рейсе */
    public static final int BASIS_DETACH = 4;
    /** прицепка в рейсе */
    public static final int BASIS_ATTACH = 5;
    /** отправление рейса */
    public static final int BASIS_DEPARTURE = 6;
    /** прибытие рейса */
    public static final int BASIS_ARRIVAL = 7;
    /** проверка вагона по чек-листу: расположение и состояние не меняет */
    public static final int BASIS_CHECK = 8;
    /** опись экипировки вагона: расположение и состояние не меняет */
    public static final int BASIS_EQUIP = 9;

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
    /** ответственный после события (wagon_responsible.usr); null - не назначен */
    private UserRef responsibleUsr;
    /** ответственный до события; null - не назначен, ответственный событием не менялся или это первая запись */
    private UserRef prevResponsibleUsr;
    /** событие открыло запись журнала ответственного: назначение ответственного */
    private boolean responsibleChanged;
    /** основание - константы BASIS_* */
    private int basisType;
    /** номер акта - у актов приёма-передачи; иначе null */
    private String actNumber;
    /** рейс - у отправления, прибытия, отцепки и прицепки; иначе null */
    private TripRef basisTrip;
    /** кто провёл документ; null - документа нет */
    private UserRef byUsr;
    /** основания, примечания из документа (прицепка, отцепка, ручная правка); null - нет */
    private String comment;
    /** проверка вагона (check_list.id) - у BASIS_CHECK; иначе null */
    private Long checkList;
    /** шаблон чек-листа - у BASIS_CHECK; иначе null */
    private CheckTemplateRef checkListTemplate;
    /** опись (equip_list.id) - у BASIS_EQUIP; иначе null */
    private Long equipList;
    /** шаблон описи - у BASIS_EQUIP; иначе null */
    private EquipTemplateRef equipListTemplate;
    /** число нарушений проверки или описи - у BASIS_CHECK и BASIS_EQUIP; иначе null */
    private Integer violationCount;
    /** основание-документ: train_wagon_change.id у BASIS_DETACH и BASIS_ATTACH, transfer_act.id у BASIS_TRANSFER;
     * иначе null */
    private Long act;
    /** текущий сотрудник может отменить основание: акт (Ф-АКТ-06), прицепку или отцепку (Ф-АКТ-01) */
    private boolean canCancel;
}
