package kz.zhsr.app.service;

import kz.kis.kisdbtools.repository.Db;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Защита от повторной отправки с телефона. Телефон присваивает отправке guid и при обрыве связи повторяет её: сервер
 * мог всё принять, а ответ - потеряться. Сервер выдаёт квитанцию той же транзакцией, что и запись данных, поэтому
 * повтор определяется по квитанции, а при отказе сервера guid не остаётся занятым. Отправка без guid (с десктопа) не
 * проверяется.
 */
@Service
public class OfflineReceiptService {

    private static final int GUID_LENGTH = 36;

    private final Db db;

    public OfflineReceiptService(Db db) {
        this.db = db;
    }

    /**
     * Принять отправку. Вызывается в транзакции отправки, до всех её проверок: повтор уже принятой отправки не должен
     * получить отказ из-за того, что случилось после неё.
     *
     * @param guid guid отправки; null - отправки без guid не проверяются
     * @return true - отправка новая, false - повтор
     * @throws ApiException 400 - guid длиннее допустимого
     */
    public boolean accept(String guid) throws Exception {
        if (guid == null) {
            return true;
        }
        if (guid.length() > GUID_LENGTH) {
            throw ApiException.badRequest("Неверный идентификатор отправки");
        }
        Map<String, Object> params = Map.of("guid", guid, "receivedDt", OffsetDateTime.now());
        return db.loadSqlRec(OfflineReceiptSql.accept(), params) != null;
    }

    /**
     * Записать заявку, созданную отправкой: при повторе по ней телефон узнает id заявки.
     */
    public void link(String guid, long repairRequest) throws Exception {
        if (guid != null) {
            db.updateRec("offline_receipt", Map.of("repair_request", repairRequest), Map.of("client_guid", guid));
        }
    }

    /**
     * Заявка, созданная уже принятой отправкой.
     */
    public long requestOf(String guid) throws Exception {
        return db.loadList("offline_receipt", Map.of("client_guid", guid)).get(0).getLong("repair_request");
    }

}
