package kz.zhsr.app.service;

/**
 * SQL журнала вагона ({@link WagonService}).
 */
final class WagonSql {

    private WagonSql() {
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

    /** Акты отцепки и прицепки вагона и записи журналов, которые они открыли. Параметр wagon. */
    static String couplingActs() {
        return """
                select coupling_act_wagon.wagon_location, coupling_act_wagon.wagon_status, coupling_act.id,
                       coupling_act.number, coupling_act.coupling_type, coupling_act.trip,
                       coupling_act.created_by_usr, coupling_act.reason
                from coupling_act_wagon
                join coupling_act on coupling_act.id = coupling_act_wagon.coupling_act
                where coupling_act_wagon.wagon = :wagon
                """;
    }

    /** Акты приёма-передачи вагона и записи журнала расположения, которые они открыли. Параметр wagon. */
    static String transferActs() {
        return """
                select transfer_act_wagon.wagon_location, transfer_act.id, transfer_act.number,
                       transfer_act.received_by_usr
                from transfer_act_wagon
                join transfer_act on transfer_act.id = transfer_act_wagon.transfer_act
                where transfer_act_wagon.wagon = :wagon and transfer_act_wagon.wagon_location is not null
                """;
    }

    /** Рейсы вагона и записи журналов, которые открыли отправление и прибытие. Параметр wagon. */
    static String tripWagons() {
        return """
                select trip_wagon.trip, trip_wagon.depart_wagon_location, trip_wagon.depart_wagon_status,
                       trip_wagon.arrive_wagon_location, trip_wagon.arrive_wagon_status, trip.departed_by_usr,
                       trip.arrived_by_usr
                from trip_wagon
                join trip on trip.id = trip_wagon.trip
                where trip_wagon.wagon = :wagon
                """;
    }

}
