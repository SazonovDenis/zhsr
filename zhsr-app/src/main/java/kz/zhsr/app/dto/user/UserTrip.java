package kz.zhsr.app.dto.user;

import kz.zhsr.app.auth.CurrentUser;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Текущий рейс сотрудника для фронта (UserTrip в api/session.ts): trainNumber и name - маршрута рейса
 * (route), tripStatusType - рейса, crewDutyType - строки бригады (trip_crew); departure, arrival и wagons (номера
 * вагонов) - как у {@link CurrentUser.Trip}; plannedDt, departedDt, arrivedDt - рейса (trip): плановое
 * отправление, фактические отправление и прибытие; departureMarkedDt, arrivalMarkedDt - когда эти отметки внесли.
 */
public record UserTrip(long id, String trainNumber, String name, long tripStatusType, OffsetDateTime departure,
                       OffsetDateTime arrival, long crewDutyType, List<String> wagons, OffsetDateTime plannedDt,
                       OffsetDateTime departedDt, OffsetDateTime arrivedDt, OffsetDateTime departureMarkedDt,
                       OffsetDateTime arrivalMarkedDt) {
}
