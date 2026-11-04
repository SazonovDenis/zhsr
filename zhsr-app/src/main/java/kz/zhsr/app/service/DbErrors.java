package kz.zhsr.app.service;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * Ошибки ограничений БД - в ошибки для пользователя ({@link ApiException}): запись с таким значением уже есть, запись
 * используется, значение не проходит проверку.
 */
public final class DbErrors {

    private DbErrors() {
    }

    /**
     * @param e ошибка записи в БД
     * @return ApiException, если причина - нарушенное ограничение; иначе e как есть
     */
    public static Exception translate(Exception e) {
        PSQLException pg = find(e);
        if (pg == null || pg.getSQLState() == null) {
            return e;
        }
        ServerErrorMessage msg = pg.getServerErrorMessage();
        String constraint = msg == null || msg.getConstraint() == null ? "" : " (" + msg.getConstraint() + ")";
        return switch (pg.getSQLState()) {
            case "23505" -> ApiException.conflict("Запись с таким значением уже есть" + constraint);
            case "23503" -> ApiException.conflict("Запись связана с другими данными" + constraint
                    + ": её нельзя удалить, а ссылка должна вести на существующую запись");
            case "23514", "23P01" -> ApiException.badRequest("Значение не проходит проверку" + constraint);
            case "23502" -> ApiException.badRequest("Не заполнено обязательное поле"
                    + (msg == null || msg.getColumn() == null ? "" : " (" + msg.getColumn() + ")"));
            default -> e;
        };
    }

    private static PSQLException find(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof PSQLException pg) {
                return pg;
            }
        }
        return null;
    }

}
