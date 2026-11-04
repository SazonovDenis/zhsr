package kz.zhsr.app.controller;

import kz.zhsr.app.service.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ошибки - в виде {"message": "..."}: фронт показывает message пользователю.
 */
@RestControllerAdvice
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Недостаточно прав"));
    }

    // параметр запроса не разбирается: число не число, дата не дата; в тексте - имена параметров и их значения
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, String>> badParam(BindException e) {
        log.warn("Неверный параметр запроса: {}", e.getMessage());
        String params = e.getFieldErrors().stream()
                .map(f -> f.getField() + "=" + f.getRejectedValue())
                .collect(Collectors.joining(", "));
        return badParam(params);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> badParam(MethodArgumentTypeMismatchException e) {
        log.warn("Неверный параметр запроса: {}", e.getMessage());
        return badParam(e.getName() + "=" + e.getValue());
    }

    private static ResponseEntity<Map<String, String>> badParam(String params) {
        String message = params.isEmpty() ? "Неверный параметр запроса" : "Неверный параметр запроса: " + params;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    // тело запроса не разбирается: не JSON, дробное число в целом поле и т. п.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> badBody(HttpMessageNotReadableException e) {
        log.warn("Неверное тело запроса: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Неверные данные запроса"));
    }

    // фото больше предела spring.servlet.multipart (application.properties)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "Файл слишком большой: фото - до 20 МБ, все фото запроса - до 110 МБ"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> other(Exception e) {
        log.error("Ошибка запроса", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Внутренняя ошибка сервера"));
    }

}
