package kz.zhsr.app.dto;

/**
 * Вагон в акте отцепки или прицепки.
 *
 * @param wagonType    тип вагона (wagon_type)
 * @param locationType вид расположения (location_type)
 * @param place        место расположения по виду: станция, депо, парк отстоя или рейс
 * @param wagonStatusType состояние вагона (wagon_status_type)
 * @param otherTrain   номер поезда другого активного рейса, в составе которого вагон; null - такого рейса нет
 * @param available    вагон можно отцепить или прицепить; иначе показан, но отметить нельзя
 */
public record CouplingWagon(String number, long wagonType, long locationType, long place, long wagonStatusType, String otherTrain,
                            boolean available) {
}
