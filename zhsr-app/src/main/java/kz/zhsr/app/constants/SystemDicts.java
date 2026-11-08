package kz.zhsr.app.constants;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.repository.Db;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Системные справочники: таблица - класс констант. Значения меняются только вместе с кодом: данные - db/dict/*.tsv,
 * их загружает Liquibase. В администрировании такие таблицы не редактируются.
 * <p>
 * При старте приложения сверяет каждую таблицу с её классом: коды и id записей таблицы должны совпадать с
 * константами класса (имя константы - code, значение - id). Расхождение - ошибка, приложение не стартует.
 */
@Component
public class SystemDicts implements ApplicationRunner {

    /**
     * Классы констант системных справочников; таблица - поле TABLE класса.
     */
    public static final List<Class<?>> CLASSES = List.of(
            ActStatus.class,
            ConductorCategory.class,
            CouplingType.class,
            CrewDuty.class,
            LocationType.class,
            RequestStatus.class,
            RoleType.class,
            ServiceType.class,
            Severity.class,
            TransportType.class,
            TripStatus.class,
            WagonStatus.class,
            WorkType.class
    );

    private final Db db;

    public SystemDicts(Db db) {
        this.db = db;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> errors = new ArrayList<>();
        for (Class<?> cls : CLASSES) {
            String table = table(cls);
            Map<String, Long> expected = constants(cls);
            Map<String, Long> actual = new TreeMap<>();
            for (DbRec rec : db.loadList(table, null)) {
                actual.put(rec.getString("code"), rec.getLong("id"));
            }
            if (!expected.equals(actual)) {
                errors.add(table + ": в БД " + actual + ", в " + cls.getSimpleName() + " " + expected);
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Системные справочники не совпадают с константами кода:\n"
                    + String.join("\n", errors));
        }
    }

    /**
     * Таблица справочника: поле TABLE класса.
     */
    public static String table(Class<?> cls) throws ReflectiveOperationException {
        return (String) cls.getField("TABLE").get(null);
    }

    /**
     * Константы класса: code -> id (public static final long).
     */
    public static Map<String, Long> constants(Class<?> cls) throws IllegalAccessException {
        Map<String, Long> res = new TreeMap<>();
        for (Field f : cls.getFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) && Modifier.isFinal(m) && f.getType() == long.class) {
                res.put(f.getName(), f.getLong(null));
            }
        }
        return res;
    }

}
