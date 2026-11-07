package kz.zhsr.app.dto.admin;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Сотрудник в администрировании: строка списка и карточка.
 */
@Getter
@Setter
public class AdminUserRow {
    private long id;
    private String login;
    private String fullName;
    private String phone;
    /** роли сотрудника, id role_type по возрастанию; пусто - ролей нет */
    private List<Long> roles = List.of();
    /** текущее депо (usr_depot); null - депо нет */
    private Long depot;
    /** текущая категория (usr_category); null - категории нет */
    private Long categoryType;
    private boolean disabled;
    private boolean changePassword;
}
