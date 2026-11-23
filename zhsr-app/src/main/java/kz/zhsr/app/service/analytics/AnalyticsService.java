package kz.zhsr.app.service.analytics;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kiscore.utils.UtDb;
import kz.kis.kisdbtools.ref.RefResolver;
import kz.kis.kisdbtools.repository.DbRecMapper;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.constants.CrewDuty;
import kz.zhsr.app.constants.RequestStatus;
import kz.zhsr.app.constants.ServiceType;
import kz.zhsr.app.constants.TrainWagonKind;
import kz.zhsr.app.dto.analytics.ExecutionRow;
import kz.zhsr.app.dto.analytics.OvertimeChart;
import kz.zhsr.app.dto.analytics.OvertimeRow;
import kz.zhsr.app.dto.analytics.RequestsChart;
import kz.zhsr.app.dto.analytics.RequestsDay;
import kz.zhsr.app.dto.analytics.RequestsWeek;
import kz.zhsr.app.dto.analytics.UncheckedWagonRow;
import kz.zhsr.app.dto.ref.UserRef;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Данные диаграмм раздела «Аналитика».
 */
@Service
public class AnalyticsService {

    /** Норма рабочих часов в месяц. */
    static final int NORM_HOURS = 168;

    /** Число месяцев диаграммы «переработка»: 11 полных и текущий. */
    private static final int MONTHS = 12;

    /** Период диаграммы «Заявки и неисправности», суток; начало округляется до понедельника. */
    private static final int REQUEST_DAYS = 365;

    private final Db db;
    private final RefResolver refResolver;

    public AnalyticsService(Db db, RefResolver refResolver) {
        this.db = db;
        this.refResolver = refResolver;
    }

    /**
     * Переработка (GET /analytics/overtime): часы каждого проводника и ЛНП по месяцам за 12 месяцев, включая текущий.
     * Часы рейса - часы маршрута по обязанности в бригаде; рейс относится к месяцу отправления (departed_dt).
     * Сотрудник с обеими обязанностями даёт две строки.
     */
    public OvertimeChart overtime() throws Exception {
        ZoneId zone = ZoneId.systemDefault();
        YearMonth first = YearMonth.now(zone).minusMonths(MONTHS - 1);
        List<OffsetDateTime> months = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            months.add(first.plusMonths(i).atDay(1).atStartOfDay(zone).toOffsetDateTime());
        }

        Map<String, int[]> hours = new LinkedHashMap<>();
        for (DbRec rec : db.loadSql(AnalyticsSql.crewHours(), Map.of("from", months.getFirst(),
                "lnp", CrewDuty.LNP, "conductor", CrewDuty.CONDUCTOR))) {
            OffsetDateTime departed = rec.getDateTimeTZ("departed_dt");
            YearMonth month = YearMonth.from(departed.atZoneSameInstant(zone));
            int index = (int) (month.getYear() * 12L + month.getMonthValue()
                    - first.getYear() * 12L - first.getMonthValue());
            if (index < 0 || index >= MONTHS) {
                continue;
            }
            String key = rec.getLong("usr") + ":" + rec.getLong("crew_duty_type");
            hours.computeIfAbsent(key, k -> new int[MONTHS])[index] += rec.getLong("hours").intValue();
        }

        Map<Long, Long> depots = loadCurrentDepots();

        List<OvertimeRow> rows = new ArrayList<>();
        hours.forEach((key, values) -> {
            String[] parts = key.split(":");
            long usr = Long.parseLong(parts[0]);
            OvertimeRow row = new OvertimeRow();
            row.setUsr(new UserRef(usr));
            row.setCrewDutyType(Long.parseLong(parts[1]));
            row.setDepot(depots.get(usr));
            row.setHours(Arrays.stream(values).boxed().toList());
            rows.add(row);
        });
        refResolver.resolve(rows);
        return new OvertimeChart(months, NORM_HOURS, rows);
    }

    /**
     * Давно не проверенные вагоны (GET /analytics/unchecked-wagons): каждый вагон в эксплуатации, его последний
     * чек-лист, последняя опись и последний отправленный рейс. Пустой список - вагонов в эксплуатации нет.
     *
     * @param at по состоянию на момент; null - на текущий момент
     */
    public List<UncheckedWagonRow> uncheckedWagons(OffsetDateTime at) throws Exception {
        List<UncheckedWagonRow> rows = DbRecMapper.toList(
                db.loadSql(AnalyticsSql.lastChecks(), Map.of("fact", TrainWagonKind.FACT,
                        "at", at == null ? OffsetDateTime.now() : at)), UncheckedWagonRow.class);
        refResolver.resolve(rows);
        return rows;
    }

    /**
     * Заявки и неисправности (GET /analytics/requests): заявки и отправленные рейсы по дням за год, сроки исполнения
     * по неделям подачи. Период - с понедельника недели, в которую попадает день год назад, по сегодня.
     *
     * @param service служба заявок (ServiceType); null - все службы
     */
    public RequestsChart requests(Long service) throws Exception {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate first = today.minusDays(REQUEST_DAYS - 1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int dayCount = (int) ChronoUnit.DAYS.between(first, today) + 1;
        int weekCount = (dayCount + 6) / 7;
        OffsetDateTime from = first.atStartOfDay(zone).toOffsetDateTime();

        int[] requests = new int[dayCount];
        List<List<Double>> hours = new ArrayList<>();
        for (int i = 0; i < weekCount; i++) {
            hours.add(new ArrayList<>());
        }
        for (DbRec rec : loadRequests(from, service)) {
            OffsetDateTime created = rec.getDateTimeTZ("created_dt");
            int index = dayIndex(first, created, zone);
            if (index >= dayCount) {
                continue;
            }
            requests[index]++;
            OffsetDateTime done = rec.getDateTimeTZ("done_dt");
            if (done != null) {
                hours.get(index / 7).add(Duration.between(created, done).toMinutes() / 60.0);
            }
        }

        int[] trips = new int[dayCount];
        for (DbRec rec : db.loadSql(AnalyticsSql.departures(), Map.of("from", from))) {
            int index = dayIndex(first, rec.getDateTimeTZ("departed_dt"), zone);
            if (index < dayCount) {
                trips[index]++;
            }
        }

        List<RequestsDay> days = new ArrayList<>();
        for (int i = 0; i < dayCount; i++) {
            days.add(new RequestsDay(first.plusDays(i), requests[i], trips[i]));
        }
        List<RequestsWeek> weeks = new ArrayList<>();
        for (int i = 0; i < weekCount; i++) {
            List<Double> values = hours.get(i);
            Collections.sort(values);
            weeks.add(new RequestsWeek(first.plusWeeks(i), values.size(), typical(values), median(values)));
        }
        return new RequestsChart(days, weeks);
    }

    /**
     * Скорость и качество исполнения заявок (GET /analytics/execution): исполнители технической службы и время
     * исполнения заявок, поданных за год. Время исполнения - от последнего взятия в работу перед первым выполнением до
     * первого выполнения. Заявка относится к исполнителю в момент первого выполнения; возвращена на доработку - снова
     * в работе после первого выполнения. Пустой список - выполненных заявок нет.
     */
    public List<ExecutionRow> execution() throws Exception {
        OffsetDateTime from = LocalDate.now(ZoneId.systemDefault()).minusDays(REQUEST_DAYS - 1)
                .atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        Map<Long, List<DbRec>> requests = UtDb.collectGroupBy_records(db.loadSql(AnalyticsSql.execution(),
                Map.of("from", from, "service", ServiceType.TECHNICAL, "inProgress", RequestStatus.IN_PROGRESS,
                        "done", RequestStatus.DONE)), "repair_request");
        Map<Long, Long> depots = loadCurrentDepots();

        Map<Long, ExecutionRow> rows = new LinkedHashMap<>();
        for (List<DbRec> statuses : requests.values()) {
            addExecution(rows, statuses, depots);
        }
        List<ExecutionRow> res = new ArrayList<>(rows.values());
        refResolver.resolve(res);
        return res;
    }

    /** Добавляет исполнителю заявку; statuses - взятия в работу и выполнения заявки по порядку. */
    private static void addExecution(Map<Long, ExecutionRow> rows, List<DbRec> statuses, Map<Long, Long> depots) {
        OffsetDateTime start = null;
        for (int i = 0; i < statuses.size(); i++) {
            DbRec status = statuses.get(i);
            if (status.getLong("request_status_type") == RequestStatus.IN_PROGRESS) {
                start = status.getDateTimeTZ("dbeg");
                continue;
            }
            Long executor = status.getLong("executor_usr", null);
            if (start == null || executor == null) {
                return;
            }
            ExecutionRow row = rows.computeIfAbsent(executor, usr -> {
                ExecutionRow created = new ExecutionRow();
                created.setExecutorUsr(new UserRef(usr));
                created.setDepot(depots.get(usr));
                return created;
            });
            row.getMinutes().add(Duration.between(start, status.getDateTimeTZ("dbeg")).toMinutes());
            if (statuses.subList(i + 1, statuses.size()).stream()
                    .anyMatch(s -> s.getLong("request_status_type") == RequestStatus.IN_PROGRESS)) {
                row.setReturned(row.getReturned() + 1);
            }
            return;
        }
    }

    /** Текущие депо сотрудников: usr - depot. */
    private Map<Long, Long> loadCurrentDepots() throws Exception {
        Map<Long, Long> depots = new HashMap<>();
        for (DbRec rec : db.loadSql(AnalyticsSql.currentDepots(), Map.of())) {
            depots.put(rec.getLong("usr"), rec.getLong("depot"));
        }
        return depots;
    }

    private List<DbRec> loadRequests(OffsetDateTime from, Long service) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("from", from);
        params.put("done", RequestStatus.DONE);
        params.put("closed", RequestStatus.CLOSED);
        if (service != null) {
            params.put("service", service);
        }
        return db.loadSql(AnalyticsSql.requests(service != null), params);
    }

    private static int dayIndex(LocalDate first, OffsetDateTime time, ZoneId zone) {
        return (int) ChronoUnit.DAYS.between(first, time.atZoneSameInstant(zone).toLocalDate());
    }

    /**
     * Упорядоченный список без 10% наименьших и 10% наибольших значений; число отбрасываемых с каждой стороны
     * округляется, из списка короче 5 значений ничего не отбрасывается.
     */
    private static List<Double> typical(List<Double> sorted) {
        int cut = (int) Math.round(sorted.size() * 0.1);
        return sorted.subList(cut, sorted.size() - cut);
    }

    /** Медиана упорядоченного списка; null - список пуст. */
    private static Double median(List<Double> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int size = sorted.size();
        return (sorted.get((size - 1) / 2) + sorted.get(size / 2)) / 2;
    }
}
