package kz.zhsr.app.dto.admin;

import java.util.List;

/**
 * Сотрудник из формы администрирования: новый или изменённый.
 *
 * @param login    логин
 * @param fullName ФИО полностью
 * @param phone    телефон +7XXXXXXXXXX; пусто - нет
 * @param roles    роли - id role_type; пусто - ролей нет
 * @param depot    депо; null - нет. Другое депо закрывает период прежнего и открывает новый с текущего момента
 * @param categoryType категория проводника; null - нет. Меняется так же, как депо
 * @param disabled учётная запись отключена
 * @param password новый пароль: у нового сотрудника обязателен, у существующего пусто - пароль не меняется. Заданный
 *                 пароль сотрудник должен сменить при входе
 */
public record AdminUserDraft(String login, String fullName, String phone, List<Long> roles, Long depot, Long categoryType,
                             boolean disabled, String password) {
    public AdminUserDraft {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
