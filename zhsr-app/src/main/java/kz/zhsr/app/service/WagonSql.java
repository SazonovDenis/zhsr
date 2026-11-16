package kz.zhsr.app.service;

/**
 * SQL журнала вагона ({@link WagonService}), ответственного ({@link ResponsibleService}) и пробега ({@link WagonRun}).
 */
final class WagonSql {

    private WagonSql() {
    }

    /** Общий пробег вагона в кэше: пробег на момент ввода плюс участки пробега. Параметр wagon. */
    static String updateRunKm() {
        return """
                update wagon_info
                set run_km = (select wagon.initial_run_km from wagon where wagon.id = :wagon)
                             + (select coalesce(sum(wagon_run.km), 0) from wagon_run
                                where wagon_run.wagon = :wagon)
                where wagon_info.wagon = :wagon
                """;
    }

    /** Акт приёма-передачи, который ждёт приёма по вагону. Параметры wagon, pending - статус «ожидает приёма». */
    static String pendingTransfer() {
        return """
                select transfer_act.id
                from transfer_act_wagon
                join transfer_act on transfer_act.id = transfer_act_wagon.transfer_act
                where transfer_act_wagon.wagon = :wagon and transfer_act.act_status_type = :pending
                limit 1
                """;
    }

    /** Ручные правки вагона и записи журналов, которые они открыли. Параметр wagon. */
    static String edits() {
        return """
                select wagon_edit.id, wagon_edit.created_by_usr, wagon_edit.comment, wagon_edit.wagon_location,
                       wagon_edit.wagon_status, wagon_edit.wagon_responsible
                from wagon_edit
                where wagon_edit.wagon = :wagon
                """;
    }

    /** Станция прибытия рейса: последняя остановка маршрута. Параметр trip. */
    static String tripArrivalStation() {
        return """
                select route_station.station
                from trip
                join route_station on route_station.route = trip.route and route_station.have_stop
                where trip.id = :trip
                order by route_station.seq desc
                limit 1
                """;
    }

    /**
     * Сотрудники депо станции (текущая запись usr_depot), включённые. Параметр station.
     */
    static String depotStaff() {
        return """
                select usr.id
                from usr
                where not usr.disabled
                  and usr.id in (select usr_depot.usr from usr_depot
                                 join depot on depot.id = usr_depot.depot
                                 where usr_depot.dend is null and depot.station = :station)
                order by usr.full_name, usr.id
                """;
    }

    /**
     * Бригады рейсов, включённые сотрудники: действующие строки бригады (незакрытые или закрытые прибытием рейса).
     * Параметр trips.
     */
    static String crewCandidates() {
        return """
                select trip_crew.usr, trip_crew.trip, trip_crew.crew_duty_type
                from trip_crew
                join trip on trip.id = trip_crew.trip
                join usr on usr.id = trip_crew.usr
                where trip_crew.trip = any(:trips) and not usr.disabled
                  and (trip_crew.dend is null or trip_crew.dend = trip.arrived_dt)
                order by trip_crew.crew_duty_type desc, usr.full_name, trip_crew.id
                """;
    }

    /**
     * Поезда, в которых вагоны с номерами сейчас по факту. Параметр numbers.
     */
    static String currentTrainsOf() {
        return """
                select distinct train_wagon.train
                from train_wagon
                join wagon on wagon.id = train_wagon.wagon
                where wagon.number = any(:numbers) and train_wagon.dbeg is not null and train_wagon.dend is null
                """;
    }

    /** Записи журнала расположения вагона по времени. Параметр wagon. */
    static String locations() {
        return """
                select wagon_location.id, wagon_location.dbeg, wagon_location.dend, wagon_location.location_type,
                       coalesce(wagon_location.station, wagon_location.depot, wagon_location.yard,
                                wagon_location.trip) as place,
                       wagon_location.trip
                from wagon_location
                where wagon_location.wagon = :wagon
                order by wagon_location.dbeg
                """;
    }

    /** Записи журнала состояния вагона по времени. Параметр wagon. */
    static String statuses() {
        return """
                select wagon_status.id, wagon_status.dbeg, wagon_status.dend, wagon_status.wagon_status_type
                from wagon_status
                where wagon_status.wagon = :wagon
                order by wagon_status.dbeg
                """;
    }

    /** Записи журнала ответственного за вагон по времени. Параметр wagon. */
    static String responsibles() {
        return """
                select wagon_responsible.id, wagon_responsible.dbeg, wagon_responsible.dend, wagon_responsible.usr
                from wagon_responsible
                where wagon_responsible.wagon = :wagon
                order by wagon_responsible.dbeg
                """;
    }

    /** Прицепки и отцепки вагона в рейсе и записи журналов, которые они открыли. Параметр wagon. */
    static String changes() {
        return """
                select train_wagon_change.id, train_wagon_change.change_type, train_wagon_change.trip,
                       train_wagon_change.usr, train_wagon_change.comment, train_wagon_change.wagon_location,
                       train_wagon_change.wagon_status, train_wagon_change.wagon_responsible
                from train_wagon_change
                join train_wagon on train_wagon.id = train_wagon_change.train_wagon
                where train_wagon.wagon = :wagon and train_wagon_change.wagon_location is not null
                """;
    }

    /**
     * Акты приёма-передачи вагона и записи журналов, которые они открыли: расположения - приёмом, ответственного -
     * оформлением. Параметр wagon.
     */
    static String transferActs() {
        return """
                select transfer_act_wagon.wagon_location, transfer_act_wagon.wagon_responsible, transfer_act.id,
                       transfer_act.number, transfer_act.transferred_by_usr, transfer_act.received_by_usr,
                       transfer_act.act_status_type
                from transfer_act_wagon
                join transfer_act on transfer_act.id = transfer_act_wagon.transfer_act
                where transfer_act_wagon.wagon = :wagon
                """;
    }

}
