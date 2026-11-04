package kz.zhsr.app.dto;

/**
 * Вагон для добавления в состав рейса: на станции отправления, в депо или в парке отстоя при ней.
 *
 * @param wagonType    тип вагона (wagon_type)
 * @param locationType вид расположения (location_type)
 * @param place        место расположения по виду: станция, депо или парк отстоя
 * @param wagonStatusType состояние вагона (wagon_status_type)
 */
public record WagonCandidate(String number, long wagonType, long locationType, long place, long wagonStatusType) {
}
