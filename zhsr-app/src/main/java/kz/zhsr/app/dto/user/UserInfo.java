package kz.zhsr.app.dto.user;

import java.util.List;
import java.util.Map;

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
 * @param otherTrip    соседний рейс: предыдущий или следующий относительно текущего, для показа по ссылке; null - нет
 * @param dbGuid       идентификатор серверной БД (db_info): фронт сверяет с ним локальную БД браузера
 * @param dictVersions версии всех справочников: имя справочника фронта → версия
 */
public record UserInfo(long id, String name, String firstName, List<Long> roles, List<Long> services,
                       Long depot, Long depotStation, UserTrip trip, UserTrip otherTrip,
                       String dbGuid, Map<String, String> dictVersions) {
}
