package kz.zhsr.app.service;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.constants.TrainWagonChangeType;
import kz.zhsr.app.constants.TrainWagonKind;
import kz.zhsr.app.constants.TrainWagonPlanStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Участие вагонов в поезде (train_wagon) по журналу поезда: прицепки и отцепки (train_wagon_change) и смены порядка
 * вагонов (train_wagon_seq) - техника «журнал и состояние» (doc/db-doc/journal-state-plan-fact.md). Группа - вагоны
 * поезда: прицепка на позицию сдвигает вагоны с неё на 1 назад, отцепка - вагоны за отцепленным на 1 вперёд, смена
 * порядка переставляет вагоны ({@link #reorder}); каждое изменение позиции закрывает запись участия вагона и создаёт
 * новую.
 * <p>
 * Слой Факт - история: записи Факта журнала применяются при создании, временем не раньше последнего Факта поезда.
 * Слой План - ожидаемый состав: перестраивается целиком при каждом изменении журнала поезда - открытые записи Факта
 * копируются, затем применяются ожидающие записи Плана журнала; просроченный план - временем последнего Факта поезда.
 * Проверки и права - у вызывающего ({@link TrainService}); все методы вызываются внутри транзакции.
 */
@Service
public class TrainJournal {

    /** Вагон в составе: позиция и открытая запись участия. */
    private static final class Slot {
        final long wagon;
        /** train_wagon.id открытой записи; 0 - записи ещё нет */
        long record;
        int seq;
        OffsetDateTime dbeg;

        Slot(long wagon) {
            this.wagon = wagon;
        }
    }

    private final Db db;
    private final TrainJournalStore store;

    public TrainJournal(Db db, TrainJournalStore store) {
        this.db = db;
        this.store = store;
    }

    // --- чтение

    /** Время последней записи Факта журнала поезда; фактов нет - null. */
    public OffsetDateTime lastFact(long train) throws Exception {
        DbRec rec = db.loadSqlRec(TrainSql.lastTrainFact(), Map.of("train", train, "fact", TrainWagonKind.FACT));
        return rec == null || rec.get("dt") == null ? null : rec.getDateTimeTZ("dt");
    }

    /** Время последней записи Факта журнала вагона во всех поездах; фактов нет - null. */
    public OffsetDateTime lastWagonFact(long wagon) throws Exception {
        DbRec rec = db.loadSqlRec(TrainSql.lastWagonFact(), Map.of("wagon", wagon, "fact", TrainWagonKind.FACT));
        return rec == null || rec.get("dt") == null ? null : rec.getDateTimeTZ("dt");
    }

    /**
     * Записи участия поезда слоя kind на момент time, по позиции.
     *
     * @param kind TrainWagonKind
     */
    public List<DbRec> at(long train, int kind, OffsetDateTime time) throws Exception {
        return db.loadSql(TrainSql.statesAt(), Map.of("train", train, "kind", kind, "time", time));
    }

    /**
     * Факт прицепки, с которого началось участие вагона в поезде, действующее в момент time: последняя запись Факта
     * прицепки вагона к поезду не позже time; нет - null.
     */
    public Long attachOf(long train, long wagon, OffsetDateTime time) throws Exception {
        DbRec rec = db.loadSqlRec(TrainSql.attachOf(), Map.of("train", train, "wagon", wagon, "time", time,
                "fact", TrainWagonKind.FACT, "attach", TrainWagonChangeType.ATTACH));
        return rec == null ? null : rec.getLong("id");
    }

    // --- изменение

    /**
     * Применить к слою Факт записи Факта журнала поезда со временем time (п. 5 и 7 техники): записи Факта участия
     * поезда, созданные с этого времени, удаляются, закрытые с этого времени - снова открыты, затем записи Факта
     * журнала поезда с этого времени применяются по порядку. time - время новой записи Факта (не раньше последнего
     * Факта поезда) или удалённой (любое: оставшиеся Факты с этого времени применяются заново).
     */
    public void applyFact(long train, OffsetDateTime time) throws Exception {
        Map<String, Object> params = Map.of("train", train, "kind", TrainWagonKind.FACT, "time", time);
        db.execSql(TrainSql.deleteStatesFrom(), params);
        db.execSql(TrainSql.reopenStatesFrom(), params);
        List<Slot> order = new ArrayList<>();
        for (DbRec r : db.loadSql(TrainSql.openStates(), Map.of("train", train, "kind", TrainWagonKind.FACT))) {
            order.add(slot(r));
        }
        List<DbRec> events = store.list(Map.of("train", train, "kind", TrainWagonKind.FACT)).stream()
                .filter(r -> !r.getDateTimeTZ("dt").isBefore(time))
                .toList();
        play(train, TrainWagonKind.FACT, order, events, null);
    }

    /**
     * Перестроить слой План поезда - ожидаемый состав (п. 5 техники): записи Плана участия удаляются; открытые записи
     * Факта копируются с тем же началом; ожидающие записи Плана журнала применяются по порядку времени, просроченные -
     * временем последнего Факта поезда.
     */
    public void rebuildPlan(long train) throws Exception {
        db.deleteRec("train_wagon", Map.of("train", train, "kind", TrainWagonKind.PLAN));
        List<Slot> order = new ArrayList<>();
        for (DbRec r : db.loadSql(TrainSql.openStates(), Map.of("train", train, "kind", TrainWagonKind.FACT))) {
            Slot s = slot(r);
            s.record = insert(train, s.wagon, TrainWagonKind.PLAN, s.seq, s.dbeg);
            order.add(s);
        }
        OffsetDateTime last = lastFact(train);
        List<DbRec> plans = new ArrayList<>(store.list(Map.of("train", train, "kind", TrainWagonKind.PLAN,
                "plan_status", TrainWagonPlanStatus.WAITING)));
        plans.sort(Comparator.<DbRec, OffsetDateTime>comparing(e -> timeOf(e, last))
                .thenComparing(TrainJournalStore.JOURNAL_ORDER));
        play(train, TrainWagonKind.PLAN, order, plans, last);
    }

    // --- внутреннее

    /**
     * Применить события по порядку: события одного времени - вместе, у каждого вагона, позиция которого изменилась,
     * закрывается одна запись и создаётся одна.
     *
     * @param floor время, раньше которого событие не применяется (последний Факт поезда); null - время события
     */
    private void play(long train, int kind, List<Slot> order, List<DbRec> events, OffsetDateTime floor)
            throws Exception {
        int i = 0;
        while (i < events.size()) {
            OffsetDateTime time = timeOf(events.get(i), floor);
            List<Slot> was = new ArrayList<>(order);
            while (i < events.size() && timeOf(events.get(i), floor).isEqual(time)) {
                step(order, events.get(i));
                i++;
            }
            commit(train, kind, was, order, time);
        }
    }

    /**
     * Переставить элементы списка в порядке wanted (id вагонов): вагоны из wanted меняются местами между собой - на
     * позициях, которые они занимают; вагоны, которых нет в wanted, остаются на местах; вагоны wanted, которых нет в
     * списке, пропускаются.
     */
    static <T> void reorder(List<T> list, ToLongFunction<T> wagon, List<Long> wanted) {
        List<Integer> places = new ArrayList<>();
        List<T> moved = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (wanted.contains(wagon.applyAsLong(list.get(i)))) {
                places.add(i);
                moved.add(list.get(i));
            }
        }
        moved.sort(Comparator.comparingInt(item -> wanted.indexOf(wagon.applyAsLong(item))));
        for (int i = 0; i < places.size(); i++) {
            list.set(places.get(i), moved.get(i));
        }
    }

    /**
     * Прицепка ставит вагон на позицию (больше числа вагонов - последним), отцепка убирает, повтор не меняет; смена
     * порядка переставляет вагоны.
     */
    private static void step(List<Slot> order, DbRec event) {
        if (TrainJournalStore.isSeq(event)) {
            reorder(order, s -> s.wagon, TrainJournalStore.order(event));
            return;
        }
        long wagon = event.getLong("wagon");
        int index = -1;
        for (int j = 0; j < order.size(); j++) {
            if (order.get(j).wagon == wagon) {
                index = j;
            }
        }
        if (event.getInt("change_type") == TrainWagonChangeType.ATTACH) {
            if (index >= 0) {
                return;
            }
            int pos = Math.min(Math.max(event.getInt("seq"), 1), order.size() + 1);
            order.add(pos - 1, new Slot(wagon));
        } else if (index >= 0) {
            order.remove(index);
        }
    }

    /**
     * Записать изменения состава временем time: у вагона, который ушёл или сменил позицию, запись закрывается (запись
     * этого же времени - удаляется); у вагона без открытой записи - создаётся. Сначала закрытие, затем создание.
     */
    private void commit(long train, int kind, List<Slot> was, List<Slot> order, OffsetDateTime time)
            throws Exception {
        for (Slot s : was) {
            int index = order.indexOf(s);
            if (s.record != 0 && (index < 0 || index + 1 != s.seq)) {
                if (s.dbeg.isEqual(time)) {
                    db.deleteRec("train_wagon", s.record);
                } else {
                    db.updateRec("train_wagon", Map.of("id", s.record, "dend", time));
                }
                s.record = 0;
            }
        }
        for (int j = 0; j < order.size(); j++) {
            Slot s = order.get(j);
            if (s.record == 0) {
                s.seq = j + 1;
                s.dbeg = time;
                s.record = insert(train, s.wagon, kind, s.seq, time);
            }
        }
    }

    private long insert(long train, long wagon, int kind, int seq, OffsetDateTime dbeg) throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("train", train);
        values.put("wagon", wagon);
        values.put("kind", kind);
        values.put("seq", seq);
        values.put("dbeg", dbeg);
        return db.insertRec("train_wagon", values);
    }

    private static Slot slot(DbRec r) {
        Slot s = new Slot(r.getLong("wagon"));
        s.record = r.getLong("id");
        s.seq = r.getInt("seq");
        s.dbeg = r.getDateTimeTZ("dbeg");
        return s;
    }

    /** Время применения события: его время, но не раньше floor. */
    private static OffsetDateTime timeOf(DbRec event, OffsetDateTime floor) {
        OffsetDateTime dt = event.getDateTimeTZ("dt");
        return floor != null && dt.isBefore(floor) ? floor : dt;
    }

}
