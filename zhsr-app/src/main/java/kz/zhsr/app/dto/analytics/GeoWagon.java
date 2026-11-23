package kz.zhsr.app.dto.analytics;

import kz.zhsr.app.dto.ref.WagonRef;
import lombok.Getter;
import lombok.Setter;

/**
 * Положения вагона на карте.
 */
@Getter
@Setter
public class GeoWagon extends GeoPlaces {
    private WagonRef wagon;
}
