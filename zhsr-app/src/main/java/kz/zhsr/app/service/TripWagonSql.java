package kz.zhsr.app.service;

import kz.zhsr.app.constants.TrainWagonKind;
import kz.zhsr.app.constants.TripStatus;

import java.util.Map;

/**
 * Вагоны поезда рейса в запросах видимости и списков: запись участия в поезде (train_wagon) поезда рейса, слой - по
 * статусу рейса (техника «журнал и состояние», п. 11): до отправления - ожидаемый состав, после - факт; запись не
 * закончилась до отправления (фактического или планового).
 */
public final class TripWagonSql {

    private TripWagonSql() {
    }

    /** Условие на train_wagon рейса trip (таблицы train_wagon и trip должны быть в запросе, trip.train = train_wagon.train). */
    public static final String WHERE = """
            train_wagon.kind = (case when trip.trip_status_type = :tripPlanned then :wagonPlan else :wagonFact end)
              and (train_wagon.dend is null or train_wagon.dend >= coalesce(trip.departed_dt, trip.planned_dt))""";

    /** Параметры условия {@link #WHERE}: запрос с ним задаёт их вместе со своими. */
    public static final Map<String, Object> PARAMS = Map.of("tripPlanned", TripStatus.PLANNED,
            "wagonPlan", TrainWagonKind.PLAN, "wagonFact", TrainWagonKind.FACT);

    /** Вагоны поезда рейса :param (и {@link #PARAMS}) - подзапрос «(select train_wagon.wagon ...)» для условия «wagon in». */
    public static String wagonsOf(String param) {
        return """
                (select train_wagon.wagon from train_wagon
                 join trip on trip.train = train_wagon.train
                 where trip.id = :%s
                   and %s)""".formatted(param, WHERE);
    }

}
