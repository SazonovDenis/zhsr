package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Поезд с вагонами и рейсами (Ф-ПОЕ-02).
 */
@Getter
@Setter
public class TrainCard extends TrainRow {
    /** вагоны поезда сейчас и с плановой прицепкой, по порядку */
    private List<TrainWagon> trainWagons = List.of();
    /** рейсы поезда в статусе «план» и «выполняется», по плановому отправлению */
    private List<TripRef> trips = List.of();
    /** текущий сотрудник может менять поезд: планировщик вагонов или администратор */
    private boolean canEdit;
}
