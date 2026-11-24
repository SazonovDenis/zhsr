package kz.zhsr.app.service;

/**
 * SQL журнала вагона ({@link WagonService}), ответственных ({@link ResponsibleService}, {@link WagonJournal}) и пробега ({@link WagonRun}).
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
                       wagon_edit.wagon_status, wagon_edit.wagon_responsible, wagon_edit.closed_wagon_responsible
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
                order by usr.full_name, usr.login
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
                order by usr.full_name, usr.login, trip.planned_dt
                """;
    }

    /** Поезда, в которых вагоны сейчас по факту. Параметры wagons, fact - слой «факт» участия в поезде. */
    static String currentTrainsOf() {
        return """
                select distinct train_wagon.train
                from train_wagon
                where train_wagon.wagon = any(:wagons) and train_wagon.kind = :fact and train_wagon.dend is null
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

    /** Записи факта журнала ответственных за вагон по времени. Параметры wagon, fact - слой «факт». */
    static String responsibles() {
        return """
                select wagon_responsible.id, wagon_responsible.dbeg, wagon_responsible.dend,
                       wagon_responsible.created_by_usr
                from wagon_responsible
                where wagon_responsible.wagon = :wagon and wagon_responsible.kind = :fact
                order by wagon_responsible.dbeg
                """;
    }

    /** Последняя запись факта журнала ответственных вагона. Параметры wagon, fact - слой «факт». */
    static String lastResponsibleFact() {
        return """
                select wagon_responsible.id, wagon_responsible.dbeg, wagon_responsible.dend
                from wagon_responsible
                where wagon_responsible.wagon = :wagon and wagon_responsible.kind = :fact
                order by wagon_responsible.dbeg desc
                limit 1
                """;
    }

    /**
     * Текущие ответственные вагонов: состав открытых записей факта, по ФИО. Параметры wagons, fact - слой «факт».
     */
    static String currentResponsibleUsrs() {
        return """
                select wagon_responsible.wagon, wagon_responsible_usr.usr
                from wagon_responsible
                join wagon_responsible_usr on wagon_responsible_usr.wagon_responsible = wagon_responsible.id
                join usr on usr.id = wagon_responsible_usr.usr
                where wagon_responsible.wagon = any(:wagons) and wagon_responsible.kind = :fact
                  and wagon_responsible.dend is null
                order by usr.full_name, usr.login
                """;
    }

    /** Начало текущих ответственных вагонов: открытая запись факта из кэша wagon_info. Параметр wagons. */
    static String currentResponsibleStarts() {
        return """
                select wagon_info.wagon, wagon_responsible.dbeg
                from wagon_info
                join wagon_responsible on wagon_responsible.id = wagon_info.wagon_responsible
                where wagon_info.wagon = any(:wagons)
                """;
    }

    /** Состав записей журнала ответственных, по ФИО. Параметр records. */
    static String responsibleUsrs() {
        return """
                select wagon_responsible_usr.wagon_responsible, wagon_responsible_usr.usr
                from wagon_responsible_usr
                join usr on usr.id = wagon_responsible_usr.usr
                where wagon_responsible_usr.wagon_responsible = any(:records)
                order by usr.full_name, usr.login
                """;
    }

    /** Факты прицепки и отцепки вагона в рейсе и записи журналов, которые они открыли. Параметр wagon. */
    static String changes() {
        return """
                select train_wagon_change.id, train_wagon_change.change_type, train_wagon_change.trip,
                       train_wagon_change.usr, train_wagon_change.comment, train_wagon_change.wagon_location,
                       train_wagon_change.wagon_status, train_wagon_change.wagon_responsible,
                       train_wagon_change.closed_wagon_responsible
                from train_wagon_change
                where train_wagon_change.wagon = :wagon and train_wagon_change.wagon_location is not null
                """;
    }

    /**
     * Акты приёма-передачи вагона и записи журналов, которые они открыли: расположения - приёмом, ответственного -
     * оформлением. Параметр wagon.
     */
    static String transferActs() {
        return """
                select transfer_act_wagon.wagon_location, transfer_act_wagon.wagon_responsible,
                       transfer_act_wagon.closed_wagon_responsible, transfer_act.id,
                       transfer_act.number, transfer_act.transferred_by_usr, transfer_act.received_by_usr,
                       transfer_act.act_status_type
                from transfer_act_wagon
                join transfer_act on transfer_act.id = transfer_act_wagon.transfer_act
                where transfer_act_wagon.wagon = :wagon
                """;
    }

}
