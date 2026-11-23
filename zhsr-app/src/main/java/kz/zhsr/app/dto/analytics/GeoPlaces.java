package kz.zhsr.app.dto.analytics;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Положения на карте вне рейсов: общее вагона и проводника.
 */
@Getter
@Setter
public class GeoPlaces {
    /**
     * точки по времени вне рейсов и последняя точка каждого рейса: минуты от начала периода (меньше нуля - до периода),
     * долгота, широта; не пуст
     */
    private List<double[]> points = new ArrayList<>();
    /** рейсы периода: минуты начала и конца; конец null - рейс идёт */
    private List<Double[]> trips = new ArrayList<>();
    /** до какой минуты показывается; null - в последней точке бессрочно */
    private Double until;
}
