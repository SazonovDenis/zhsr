package kz.zhsr.app.dto.analytics;

import kz.zhsr.app.dto.ref.UserRef;
import lombok.Getter;
import lombok.Setter;

/**
 * Положения проводника на карте.
 */
@Getter
@Setter
public class GeoUsr extends GeoPlaces {
    private UserRef usr;
}
