package kz.zhsr.app.service;

import org.springframework.http.HttpStatus;

/**
 * Ошибка, которую видит пользователь: сообщение уходит на фронт как есть, со своим HTTP-статусом
 * (см. {@code ErrorHandler}).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    /**
     * 406: сотрудник должен сменить пароль - фронт переходит на страницу смены пароля.
     */
    public static ApiException passwordChangeRequired() {
        return new ApiException(HttpStatus.NOT_ACCEPTABLE, "Смените пароль");
    }

    /**
     * 409: изменение противоречит данным - значение уже есть, запись используется.
     */
    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

}
