package kz.zhsr.app.service;

import kz.zhsr.app.constants.TripStatus;
import kz.zhsr.app.constants.WagonResponsibleKind;
import kz.zhsr.app.constants.WagonResponsiblePlanStatus;
import kz.zhsr.app.constants.WagonResponsibleTripPoint;

import java.util.HashMap;
import java.util.Map;

/**
 * Ответственные на рейс в журнале ответственных: кто за какие вагоны отвечает в рейсе. Рейс в статусе «план» - план
 * ответственных на рейс (ожидает, стадия рейса - отправление); «выполняется» - текущий факт; «выполнен» - факт на
 * момент прибытия (запись началась до прибытия и не закончилась раньше него). Условие и параметры - для запросов
 * рейса, бригады и текущего пользователя.
 */
public final class TripLayerSql {

    private TripLayerSql() {
    }

    /**
     * Условие на запись wagon_responsible рейса trip (таблица trip должна быть в запросе). Параметры - {@link #params}.
     */
    public static final String WHERE = """
            ((trip.trip_status_type = :layerPlanned and wagon_responsible.kind = :layerPlan
              and wagon_responsible.plan_status = :layerWaiting and wagon_responsible.trip = trip.id
              and wagon_responsible.trip_point = :layerDeparture)
             or (trip.trip_status_type = :layerInProgress and wagon_responsible.kind = :layerFact
                 and wagon_responsible.dend is null)
             or (trip.trip_status_type = :layerDone and wagon_responsible.kind = :layerFact
                 and wagon_responsible.dbeg < trip.arrived_dt
                 and (wagon_responsible.dend is null or wagon_responsible.dend >= trip.arrived_dt)))""";

    /**
     * Вагоны и ответственные на рейс trip. Поля: wagon, number, usr - по номеру вагона. Параметры: trip, wagons -
     * вагоны, которые едут в поезде рейса, и {@link #params}.
     */
    static String crewWagons() {
        return """
                select wagon_responsible.wagon, wagon.number, wagon_responsible_usr.usr
                from wagon_responsible
                join wagon_responsible_usr on wagon_responsible_usr.wagon_responsible = wagon_responsible.id
                join wagon on wagon.id = wagon_responsible.wagon
                join usr on usr.id = wagon_responsible_usr.usr
                join trip on trip.id = :trip
                where wagon_responsible.wagon = any(:wagons) and %s
                order by wagon.number, usr.full_name, usr.login
                """.formatted(WHERE);
    }

    /**
     * Ответственные на рейс trip по всем вагонам. Поля: wagon, usr - по номеру вагона. Параметры: trip и
     * {@link #params}.
     */
    public static String tripWagons() {
        return """
                select wagon_responsible.wagon, wagon_responsible_usr.usr
                from wagon_responsible
                join wagon_responsible_usr on wagon_responsible_usr.wagon_responsible = wagon_responsible.id
                join wagon on wagon.id = wagon_responsible.wagon
                join usr on usr.id = wagon_responsible_usr.usr
                join trip on trip.id = :trip
                where %s
                order by wagon.number, usr.full_name, usr.login
                """.formatted(WHERE);
    }

    /** Константы условия {@link #WHERE}. */
    public static Map<String, Object> params() {
        Map<String, Object> res = new HashMap<>();
        res.put("layerPlanned", TripStatus.PLANNED);
        res.put("layerInProgress", TripStatus.IN_PROGRESS);
        res.put("layerDone", TripStatus.DONE);
        res.put("layerPlan", WagonResponsibleKind.PLAN);
        res.put("layerFact", WagonResponsibleKind.FACT);
        res.put("layerWaiting", WagonResponsiblePlanStatus.WAITING);
        res.put("layerDeparture", WagonResponsibleTripPoint.DEPARTURE);
        return res;
    }

}
