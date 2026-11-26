package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.constants.TrainJournalCode;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Записи общего журнала поезда - прицепки и отцепки (train_wagon_change) и смены порядка вагонов (train_wagon_seq).
 * У загруженной записи есть поле code - TrainJournalCode (имя таблицы); у смены порядка - ещё новый порядок
 * ({@link #order}), а полей wagon, change_type и seq нет. Запись определяют code и id.
 */
@Service
public class TrainJournalStore {

    private static final String CODE = "code";
    private static final String ORDER = "wagon_order";
    private static final Set<String> CODES = Set.of(TrainJournalCode.TRAIN_WAGON_CHANGE,
            TrainJournalCode.TRAIN_WAGON_SEQ);

    /**
     * Порядок записей журнала: по времени; в один момент смена порядка - после прицепок и отцепок; затем по id.
     */
    static final Comparator<DbRec> JOURNAL_ORDER = Comparator.<DbRec, OffsetDateTime>comparing(
                    r -> r.getDateTimeTZ("dt"))
            .thenComparing(TrainJournalStore::isSeq)
            .thenComparing(r -> r.getLong("id"));

    private final Db db;

    public TrainJournalStore(Db db) {
        this.db = db;
    }

    static String code(DbRec rec) {
        return rec.getString(CODE);
    }

    /** Это запись смены порядка вагонов. */
    static boolean isSeq(DbRec rec) {
        return TrainJournalCode.TRAIN_WAGON_SEQ.equals(code(rec));
    }

    /** Запись - прицепка или отцепка вида type (TrainWagonChangeType). */
    static boolean isType(DbRec rec, int type) {
        return !isSeq(rec) && rec.getInt("change_type") == type;
    }

    /** Новый порядок записи смены порядка: id вагонов по позициям. */
    @SuppressWarnings("unchecked")
    static List<Long> order(DbRec rec) {
        return (List<Long>) rec.get(ORDER);
    }

    /** Записи обеих таблиц по равенству полей, которые есть в обеих (как Db.loadList), в порядке журнала. */
    List<DbRec> list(Map<String, Object> params) throws Exception {
        List<DbRec> res = new ArrayList<>(list(TrainJournalCode.TRAIN_WAGON_CHANGE, params));
        res.addAll(list(TrainJournalCode.TRAIN_WAGON_SEQ, params));
        res.sort(JOURNAL_ORDER);
        return res;
    }

    /** Записи таблицы code по равенству её полей (как Db.loadList). */
    List<DbRec> list(String code, Map<String, Object> params) throws Exception {
        List<DbRec> recs = db.loadList(table(code), params);
        recs.forEach(r -> r.put(CODE, code));
        if (TrainJournalCode.TRAIN_WAGON_SEQ.equals(code) && !recs.isEmpty()) {
            Map<Long, List<DbRec>> items = UtDb.collectGroupBy_records(db.loadList("train_wagon_seq_item",
                    Map.of("train_wagon_seq", UtDb.<Long>uniqueValues(recs, "id"))), "train_wagon_seq");
            for (DbRec rec : recs) {
                rec.put(ORDER, items.getOrDefault(rec.getLong("id"), List.of()).stream()
                        .sorted(Comparator.comparingInt(r -> r.getInt("seq")))
                        .map(r -> r.getLong("wagon"))
                        .toList());
            }
        }
        return recs;
    }

    /** Запись; нет - null. */
    DbRec load(String code, long id) throws Exception {
        List<DbRec> recs = list(code, Map.of("id", id));
        return recs.isEmpty() ? null : recs.get(0);
    }

    /** Изменить запись таблицы code; values - с id. */
    void update(String code, Map<String, Object> values) throws Exception {
        db.updateRec(table(code), values);
    }

    /** Удалить запись; строки порядка смены порядка удаляются вместе с ней. */
    void delete(String code, long id) throws Exception {
        db.deleteRec(table(code), id);
    }

    /**
     * Добавить смену порядка.
     *
     * @param values поля train_wagon_seq без id
     * @param order  id вагонов по позициям
     */
    void insertSeq(Map<String, Object> values, List<Long> order) throws Exception {
        insertItems(db.insertRec(TrainJournalCode.TRAIN_WAGON_SEQ, new HashMap<>(values)), order);
    }

    /** Заменить новый порядок смены порядка id. */
    void setOrder(long id, List<Long> order) throws Exception {
        db.deleteRec("train_wagon_seq_item", Map.of("train_wagon_seq", id));
        insertItems(id, order);
    }

    private void insertItems(long seqId, List<Long> order) throws Exception {
        for (int i = 0; i < order.size(); i++) {
            db.insertRec("train_wagon_seq_item", Map.of("train_wagon_seq", seqId, "wagon", order.get(i),
                    "seq", i + 1));
        }
    }

    /** Имя таблицы по коду из запроса. */
    private static String table(String code) {
        if (!CODES.contains(code)) {
            throw ApiException.badRequest("Неизвестный вид записи журнала поезда");
        }
        return code;
    }

}
