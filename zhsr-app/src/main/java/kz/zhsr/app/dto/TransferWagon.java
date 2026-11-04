package kz.zhsr.app.dto;

/**
 * Вагон для акта приёма-передачи.
 *
 * @param wagonType    тип вагона (wagon_type)
 * @param locationType вид расположения (location_type)
 * @param place        место расположения по виду: станция, депо или парк отстоя
 * @param wagonStatusType состояние вагона (wagon_status_type)
 * @param pendingAct   номер акта, который ожидает приёма с этим вагоном; null - такого акта нет, вагон можно передать
 */
public record TransferWagon(String number, long wagonType, long locationType, long place, long wagonStatusType, String pendingAct) {
}
