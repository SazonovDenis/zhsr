package kz.zhsr.app.auth;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Вошедший сотрудник. Лежит в сеансе (principal в SecurityContext), поэтому Serializable и неизменяемый.
 *
 * @param id           usr.id
 * @param login        логин - ИИН
 * @param fullName     ФИО полностью, как в usr.full_name
 * @param roles        роли сотрудника по порядку role_type.id; пусто - ролей нет
 * @param depot        депо сотрудника, действующее сейчас (usr_depot); null - депо нет
 * @param depotStation станция депо сотрудника; null - депо нет или у депо не задана станция
 * @param trip         текущий рейс сотрудника; null - сотрудник сейчас не в бригаде рейса
 * @param mustChangePassword сотрудник должен сменить пароль (usr.change_password): до смены пароля разрешена только
 *                     смена пароля
 */
public record CurrentUser(
        long id,
        String login,
        String fullName,
        List<Role> roles,
        Long depot,
        Long depotStation,
        Trip trip,
        boolean mustChangePassword
) implements Serializable {

    /**
     * Роль сотрудника. Системный справочник: права и видимость (Visibility) проверяются по id - константы RoleType.
     *
     * @param id      role_type.id
     * @param service служба роли (service); null - служба определяется категорией неисправности
     */
    public record Role(long id, Long service) implements Serializable {
    }

    /**
     * Текущий рейс сотрудника: рейс, в бригаде которого он состоит, - выполняется, иначе ближайший по плану.
     *
     * @param id        trip.id
     * @param train     номер поезда
     * @param name      название маршрута рейса
     * @param state     состояние рейса (trip_status_type)
     * @param departure отправление: фактическое, если было, иначе плановое
     * @param arrival   плановое прибытие: плановое отправление плюс время в пути маршрута (route.duration)
     * @param duty      должность сотрудника в бригаде рейса: crew_duty_type.id (константы CrewDuty)
     * @param wagons    вагоны, за которыми закреплён сотрудник в рейсе (trip_crew.wagon); пусто - не закреплён
     * @param wagonNumbers номера этих вагонов, по порядку wagons
     */
    public record Trip(
            long id,
            String train,
            String name,
            long state,
            OffsetDateTime departure,
            OffsetDateTime arrival,
            long duty,
            List<Long> wagons,
            List<String> wagonNumbers
    ) implements Serializable {
        public Trip {
            wagons = wagons == null ? List.of() : List.copyOf(wagons);
            wagonNumbers = wagonNumbers == null ? List.of() : List.copyOf(wagonNumbers);
        }
    }

    public CurrentUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /**
     * @param role id роли - константа RoleType
     */
    public boolean hasRole(long role) {
        return roles.stream().anyMatch(r -> r.id() == role);
    }

    /**
     * Фамилия и инициалы: "Жумабаева Гульнара Сериковна" - "Жумабаева Г. С.". Одно слово - как есть.
     */
    public String shortName() {
        return shortName(fullName);
    }

    /**
     * Фамилия и инициалы из полного ФИО.
     */
    public static String shortName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder res = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            res.append(' ').append(parts[i].charAt(0)).append('.');
        }
        return res.toString();
    }

    /**
     * Имя для приветствия: второе слово ФИО; одно слово - оно само.
     */
    public String firstName() {
        String[] parts = fullName.trim().split("\\s+");
        return parts.length > 1 ? parts[1] : parts[0];
    }

}
