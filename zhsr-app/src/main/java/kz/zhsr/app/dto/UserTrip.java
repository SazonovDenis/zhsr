package kz.zhsr.app.dto;

import kz.zhsr.app.auth.CurrentUser;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Текущий рейс сотрудника для фронта (UserTrip в api/session.ts): trainNumber и name - маршрута рейса
 * (route), tripStatusType - рейса, crewDutyType - строки бригады (trip_crew); departure, arrival и wagons (номера
 * вагонов) - как у {@link CurrentUser.Trip}.
 */
public record UserTrip(long id, String trainNumber, String name, long tripStatusType, OffsetDateTime departure,
                       OffsetDateTime arrival, long crewDutyType, List<String> wagons) {
}
