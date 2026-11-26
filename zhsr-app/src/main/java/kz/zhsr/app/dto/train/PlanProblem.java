package kz.zhsr.app.dto.train;

import kz.zhsr.app.dto.ref.TrainRef;
import kz.zhsr.app.dto.ref.TripRef;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Рейс или плановая прицепка вне рейса, у вагонов которых есть неготовность: в списке проблем планов
 * (GET /plan-problems) и в предупреждениях перед сохранением (GET /trains/{id}/wagon-problems).
 */
@Getter
@Setter
public class PlanProblem {
    /** плановое отправление рейса или плановое время прицепки */
    private OffsetDateTime dt;
    private TrainRef train;
    /** рейс; null - прицепка вне рейса */
    private TripRef trip;
    /** плановая прицепка вне рейса (train_wagon_change.id); null - рейс */
    private Long trainWagonChange;
    private List<WagonProblem> problems = List.of();
}
