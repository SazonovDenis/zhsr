package kz.zhsr.app.dto;

/**
 * Смена пароля вошедшим сотрудником.
 *
 * @param oldPassword текущий пароль
 * @param newPassword новый пароль
 */
public record ChangePasswordRequest(String oldPassword, String newPassword) {
}
