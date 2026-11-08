package kz.zhsr.app.dto.wagon;

import kz.zhsr.app.constants.LocationType;

/**
 * Расположение в акте приёма-передачи: откуда или куда. Заполнено ровно одно из полей. Одинаковые расположения равны
 * (equals записи).
 *
 * @param station станция
 * @param depot   депо
 * @param yard    парк отстоя
 */
public record TransferPlace(Long station, Long depot, Long yard) {

    /** Сколько полей заполнено: правильное расположение - ровно одно. */
    public int filled() {
        return (station != null ? 1 : 0) + (depot != null ? 1 : 0) + (yard != null ? 1 : 0);
    }

    /** Вид расположения (location_type); расположение должно быть заполнено. */
    public long locationType() {
        return station != null ? LocationType.STATION : depot != null ? LocationType.DEPOT : LocationType.YARD;
    }

}
