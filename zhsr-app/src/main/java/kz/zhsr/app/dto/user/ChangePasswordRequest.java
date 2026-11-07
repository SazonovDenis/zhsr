package kz.zhsr.app.dto.user;

/**
 * Смена пароля вошедшим сотрудником.
 *
 * @param oldPassword текущий пароль
 * @param newPassword новый пароль
 */
public record ChangePasswordRequest(String oldPassword, String newPassword) {
}
