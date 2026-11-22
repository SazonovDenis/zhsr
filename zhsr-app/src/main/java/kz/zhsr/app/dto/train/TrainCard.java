package kz.zhsr.app.dto.train;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Поезд с журналом прицепок и отцепок и участием вагонов (Ф-ПОЕ-02): состав на любой момент клиент берёт из states.
 */
@Getter
@Setter
public class TrainCard extends TrainRow {
    /** журнал: записи Факта и ожидающие записи Плана, по времени */
    private List<TrainChange> changes = List.of();
    /** участие вагонов в поезде: оба слоя, вся история */
    private List<TrainState> states = List.of();
    /** вагоны из журнала и участия с текущими расположением и состоянием */
    private List<TrainWagon> trainWagons = List.of();
    /** время последнего Факта поезда: раньше него Факт не вносится, удалить можно только Факт этого времени; null -
     * фактов нет */
    private OffsetDateTime lastFactDt;
    /** рейсы поезда в статусе «план», «выполняется» и «выполнен» - отметки отправления и прибытия в журнале, по
     * плановому отправлению */
    private List<TrainTripMark> tripMarks = List.of();
    /** текущий сотрудник может менять поезд: планировщик вагонов или администратор */
    private boolean canEdit;
    /** текущий сотрудник может прицеплять и отцеплять вагоны: кто меняет поезд, а у поезда в рейсе - ещё начальник
     * поезда рейса и мастер депо станции маршрута */
    private boolean canCouple;
}
