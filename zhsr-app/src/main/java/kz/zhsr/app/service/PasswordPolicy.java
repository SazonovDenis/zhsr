package kz.zhsr.app.service;

/**
 * Требования к новому паролю - одни для смены пароля сотрудником и для пароля, который задаёт администратор.
 */
public final class PasswordPolicy {

    /**
     * Наименьшая длина пароля.
     */
    public static final int MIN_LENGTH = 6;

    private PasswordPolicy() {
    }

    /**
     * Проверить новый пароль: не пустой и не короче {@link #MIN_LENGTH} символов.
     *
     * @throws ApiException 400 - пароль не подходит
     */
    public static void check(String password) {
        if (password == null || password.isEmpty()) {
            throw ApiException.badRequest("Задайте пароль");
        }
        if (password.length() < MIN_LENGTH) {
            throw ApiException.badRequest("Пароль - не короче " + MIN_LENGTH + " символов");
        }
    }

}
