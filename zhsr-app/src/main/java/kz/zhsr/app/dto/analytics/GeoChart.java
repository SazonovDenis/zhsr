package kz.zhsr.app.dto.analytics;

import kz.zhsr.app.dto.route.StationPoint;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Данные карт «Текущие рейсы» и поезда. Время точек хода - минуты от from.
 *
 * @param from         начало периода
 * @param routes       маршруты рейсов, в «Текущих рейсах» - и действующие в периоде
 * @param trips        рейсы с поездом по порядку отправления, и неотправленные
 * @param states       участие вагонов в поездах рейсов, оба слоя
 * @param locations    расположение вагонов из states и из записей рейсов
 * @param responsibles ответственные за эти вагоны: периоды факта и ожидающие планы; из сотрудников - только проводники
 *                     рейсов
 * @param stations     точки станций
 */
public record GeoChart(OffsetDateTime from, List<GeoRoute> routes, List<GeoTrip> trips, List<GeoState> states,
                       List<GeoLocation> locations, List<GeoResponsible> responsibles, List<StationPoint> stations) {
}
