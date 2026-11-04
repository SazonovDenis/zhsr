package kz.zhsr.app.dto;

import java.util.List;

/**
 * Вошедший сотрудник для фронта (CurrentUser в api/session.ts).
 *
 * @param name         фамилия и инициалы
 * @param firstName    имя - для приветствия
 * @param roles        роли сотрудника (role_type); пусто - ролей нет
 * @param services     службы ролей сотрудника (service), без повторов; пусто - служб нет
 * @param depot        депо сотрудника (depot); null - депо нет
 * @param depotStation станция депо сотрудника (station); null - нет
 * @param trip         текущий рейс; null - сотрудник сейчас не в бригаде рейса
 */
public record UserInfo(long id, String name, String firstName, List<Long> roles, List<Long> services,
                       Long depot, Long depotStation, UserTrip trip) {
}
