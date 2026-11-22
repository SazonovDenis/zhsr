package kz.zhsr.app.service;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * Нарушение внешнего ключа (SQLSTATE 23503) в исключении: имя ключа fk_xxx и вид нарушения. Исключение ищется по
 * цепочке причин: Spring и пул соединений оборачивают PSQLException.
 */
public final class FkUtils {

    /** SQLSTATE нарушения внешнего ключа. */
    private static final String FK_VIOLATION = "23503";

    private FkUtils() {
    }

    /** Имя нарушенного внешнего ключа («fk_trip_route»); null - в исключении нет нарушения внешнего ключа. */
    public static String fkName(Throwable e) {
        ServerErrorMessage msg = fkMessage(e);
        return msg == null ? null : msg.getConstraint();
    }

    /** Нарушение внешнего ключа при удалении: на удаляемую запись ещё ссылаются (а не ссылка на несуществующую). */
    public static boolean isReferenced(Throwable e) {
        ServerErrorMessage msg = fkMessage(e);
        // детали PostgreSQL: «Key (id)=(5) is still referenced from table "trip"» - при удалении;
        // «Key (route)=(99) is not present in table "route"» - при записи ссылки
        return msg != null && msg.getDetail() != null && msg.getDetail().contains("still referenced");
    }

    /** Сообщение сервера БД о нарушении внешнего ключа; null - в исключении его нет. */
    private static ServerErrorMessage fkMessage(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof PSQLException pg && FK_VIOLATION.equals(pg.getSQLState())) {
                return pg.getServerErrorMessage();
            }
        }
        return null;
    }
}
