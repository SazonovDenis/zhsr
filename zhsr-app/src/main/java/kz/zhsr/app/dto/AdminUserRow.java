package kz.zhsr.app.dto;

import java.util.List;

/**
 * Сотрудник в администрировании: строка списка и карточка.
 *
 * @param login          логин
 * @param fullName       ФИО полностью
 * @param phone          телефон +7XXXXXXXXXX; null - нет
 * @param roles          роли - id role_type
 * @param depot          депо, действующее сейчас (usr_depot); null - нет
 * @param categoryType   категория проводника, действующая сейчас (usr_category); null - нет
 * @param disabled       учётная запись отключена
 * @param changePassword сменить пароль при входе
 */
public record AdminUserRow(long id, String login, String fullName, String phone, List<Long> roles, Long depot,
                           Long categoryType, boolean disabled, boolean changePassword) {
    public AdminUserRow {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
