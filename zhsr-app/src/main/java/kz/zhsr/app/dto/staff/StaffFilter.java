package kz.zhsr.app.dto.staff;

/**
 * Фильтр списка сотрудников.
 *
 * @param search  часть ФИО, логина или телефона
 * @param station id станции текущего депо сотрудника
 * @param trip    id рейса: сотрудник в бригаде этого рейса
 * @param train   id поезда: сотрудник в бригаде рейса этого поезда в статусе «план» или «выполняется»
 */
public record StaffFilter(String search, Long station, Long trip, Long train) {
}
