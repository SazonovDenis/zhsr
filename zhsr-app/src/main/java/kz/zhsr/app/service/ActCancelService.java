package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.auth.CurrentUserService;
import kz.zhsr.app.constants.ActStatus;
import kz.zhsr.app.constants.RoleType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Отмена акта приёма-передачи (Ф-АКТ-06): акт отмены с причиной, статус «отменён» и ссылка на акт отмены; записи
 * журналов вагонов, которые открыл акт, удаляются, прежние снова текущие. Отменяет сдавший или принявший;
 * администратор - любой акт. Акт отменяется, только пока открытые им записи журналов остаются текущими. Прицепка и
 * отцепка отменяются без акта отмены ({@link TrainService#undo}).
 */
@Service
public class ActCancelService {

    /**
     * Отмена акта.
     *
     * @param reason причина отмены
     */
    public record CancelBody(String reason) {
    }

    private final Db db;
    private final CurrentUserService currentUserService;
    private final WagonJournal wagonJournal;

    public ActCancelService(Db db, CurrentUserService currentUserService, WagonJournal wagonJournal) {
        this.db = db;
        this.currentUserService = currentUserService;
        this.wagonJournal = wagonJournal;
    }

    /**
     * Может ли сотрудник отменить акт приёма-передачи: акт не отменён, и он сдал или принял вагоны или администратор.
     */
    public static boolean canCancelTransfer(CurrentUser me, long actStatus, long transferredBy, Long receivedBy) {
        return actStatus != ActStatus.CANCELLED
                && (me.hasRole(RoleType.ADMIN) || me.id() == transferredBy || Objects.equals(me.id(), receivedBy));
    }

    /**
     * Отменить акт приёма-передачи целиком. Принятый акт возвращает вагоны на прежнее расположение; акт, ожидающий
     * приёма, снимает назначенного им ответственного; акт с отказом отменяется без изменений вагонов.
     *
     * @throws ApiException 404 - акта нет; 403 - не сдавал и не принимал; 409 - акт уже отменён или после него вагоны
     *                      менялись
     */
    @Transactional
    public void cancelTransfer(long id, CancelBody body) throws Exception {
        CurrentUser me = currentUserService.get();
        DbRec act = db.loadRec("transfer_act", id);
        if (act == null) {
            throw ApiException.notFound("Акт не найден");
        }
        if (act.getLong("act_status_type") == ActStatus.CANCELLED) {
            throw ApiException.conflict("Акт уже отменён");
        }
        if (!canCancelTransfer(me, act.getLong("act_status_type"), act.getLong("transferred_by_usr"),
                act.getLong("received_by_usr", null))) {
            throw ApiException.forbidden("Акт отменяет тот, кто сдал или принял вагоны, или администратор");
        }
        long cancellation = cancellation(me, body);
        for (DbRec row : db.loadList("transfer_act_wagon", Map.of("transfer_act", id))) {
            Long location = row.getLong("wagon_location", null);
            WagonJournal.Responsibles responsibles = TransferService.responsibles(row);
            Map<String, Object> values = new HashMap<>();
            values.put("id", row.getLong("id"));
            values.put("wagon_location", null);
            values.put("wagon_responsible", null);
            values.put("closed_wagon_responsible", null);
            db.updateRec("transfer_act_wagon", values);
            if (location != null) {
                wagonJournal.undoLocation(location);
            }
            wagonJournal.unassign(row.getLong("wagon"), responsibles);
        }
        db.updateRec("transfer_act", Map.of("id", id, "act_status_type", ActStatus.CANCELLED,
                "act_cancellation", cancellation));
    }

    /** Акт отмены: кто, когда, причина. @return id */
    private long cancellation(CurrentUser me, CancelBody body) throws Exception {
        if (body == null || body.reason() == null || body.reason().isBlank()) {
            throw ApiException.badRequest("Укажите причину отмены");
        }
        return db.insertRec("act_cancellation", Map.of("created_by_usr", me.id(), "created_dt", OffsetDateTime.now(),
                "reason", body.reason().trim()));
    }

}
