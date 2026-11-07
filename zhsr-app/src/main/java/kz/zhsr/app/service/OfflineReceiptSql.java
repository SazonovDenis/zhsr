package kz.zhsr.app.service;

/**
 * SQL квитанций об отправках с телефона ({@link OfflineReceiptService}). Логики здесь нет - только запрос.
 */
final class OfflineReceiptSql {

    private OfflineReceiptSql() {
    }

    /**
     * Выдать квитанцию: на повторную отправку она не вставляется и запрос возвращает пустой результат. Одновременный
     * повтор ждёт завершения первой транзакции. Параметры: guid, receivedDt.
     */
    static String accept() {
        return """
                insert into offline_receipt (client_guid, received_dt)
                values (:guid, :receivedDt)
                on conflict (client_guid) do nothing
                returning client_guid
                """;
    }

}
