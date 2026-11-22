package kz.zhsr.app.dto.wagon;

import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Строка таблицы вагонов: вариант выбора вагона и поля таблицы.
 */
@Getter
@Setter
public class WagonRow extends WagonOption {
    /** модель вагона (model_type); null - не задана */
    private Long modelType;
    /** депо приписки (depot); null - не задано */
    private Long depot;
    /** с какого времени вагон в этом расположении */
    private OffsetDateTime dbegLocation;
    /** состояние вагона (wagon_status_type) */
    private long wagonStatusType;
    /** с какого времени вагон в этом состоянии */
    private OffsetDateTime dbegStatus;
    /** открытая запись факта журнала ответственных (wagon_info.wagon_responsible); null - ответственных нет */
    private Long wagonResponsible;
    /** текущие ответственные за вагон, по ФИО; пусто - не назначены; второй проход */
    private List<UserRef> responsibleUsrs = new ArrayList<>();
    /** с какого времени текущие ответственные; null - не назначены; второй проход */
    private OffsetDateTime dbegResponsible;
    /** общий пробег, км (wagon_info.run_km) */
    private int runKm;
    /** изготовитель; null - не указан */
    private String maker;
    /** год постройки; null - не указан */
    private Integer buildYear;
    /** ввод в эксплуатацию; null - не указан */
    private LocalDate dbeg;
    /** вывод из эксплуатации; null - вагон в эксплуатации */
    private LocalDate dend;
    /** поезд, в котором вагон сейчас; null - вагон не в поезде; второй проход */
    private TrainRef train;
    /** рейсы в статусе «план» и «выполняется» поезда вагона, по плановому отправлению; второй проход */
    private List<WagonTrip> trips = new ArrayList<>();
    /** акт, ожидающий приёма по вагону; null - нет; второй проход */
    private WagonPendingTransfer pendingTransfer;
    /** расположение на момент фильтра at по журналу; null - момент не задан или вагона тогда ещё не было */
    private WagonPlace locationAt;
    /** состояние на момент фильтра at по журналу (wagon_status_type); null - как у locationAt */
    private Long wagonStatusTypeAt;
}
