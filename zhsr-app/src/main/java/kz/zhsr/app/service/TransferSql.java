package kz.zhsr.app.service;

/**
 * SQL актов приёма-передачи ({@link TransferService}).
 */
final class TransferSql {

    private TransferSql() {
    }

    private static final String WAGONS = """
            select wagon.number, wagon.wagon_type, wagon_info.location_type,
                   coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard) as place,
                   wagon_info.wagon_status_type,
                   (select transfer_act.number
                    from transfer_act_wagon
                    join transfer_act on transfer_act.id = transfer_act_wagon.transfer_act
                    where transfer_act_wagon.wagon = wagon.id and transfer_act.act_status_type = :pending
                    limit 1) as pending_act
            from wagon
            join wagon_info on wagon_info.wagon = wagon.id
            """;

    /**
     * Вагоны на станции. Параметры: station, pending - состояние акта «ожидает приёма».
     */
    static String stationWagons() {
        return WAGONS + """
                where wagon_info.station = :station
                order by wagon.number
                """;
    }

    /**
     * Вагоны в депо. Параметры: depot, pending.
     */
    static String depotWagons() {
        return WAGONS + """
                where wagon_info.depot = :depot
                order by wagon.number
                """;
    }

    /**
     * Вагоны в парке отстоя. Параметры: yard, pending.
     */
    static String yardWagons() {
        return WAGONS + """
                where wagon_info.yard = :yard
                order by wagon.number
                """;
    }

    /**
     * Сотрудники с ролью, чьё текущее депо - при станции. Параметры: role, station.
     */
    static String stationStaff() {
        return """
                select usr.id, usr.full_name
                from usr
                join usr_role on usr_role.usr = usr.id
                join usr_depot on usr_depot.usr = usr.id and usr_depot.dend is null
                join depot on depot.id = usr_depot.depot
                where usr_role.role_type = :role and not usr.disabled and depot.station = :station
                order by usr.full_name, usr.id
                """;
    }

    /**
     * Акты, новые первыми. Условия - по заданным параметрам.
     *
     * @param station отбор по параметру station
     * @param states  отбор по параметру states
     * @param one     отбор по параметру id
     */
    static String acts(boolean station, boolean states, boolean one) {
        return """
                select transfer_act.id, transfer_act.number, transfer_act.transfer_direction_type,
                       transfer_act.station, transfer_act.depot, transfer_act.yard, transfer_act.work_type,
                       transfer_act.act_status_type, transfer_act.transferred_dt, transfer_act.received_dt,
                       transfer_act.reject_reason, transfer_act.transferred_by_usr, transfer_act.received_by_usr,
                       (select usr.full_name from usr where usr.id = transfer_act.transferred_by_usr) as transferred_by,
                       (select usr.full_name from usr where usr.id = transfer_act.received_by_usr) as received_by
                from transfer_act
                where true
                """
                + (station ? " and transfer_act.station = :station" : "")
                + (states ? " and transfer_act.act_status_type = any(:states)" : "")
                + (one ? " and transfer_act.id = :id" : "")
                + " order by transfer_act.id desc limit 500";
    }

    /**
     * Вагоны актов. Параметр: acts.
     */
    static String actWagons() {
        return """
                select transfer_act_wagon.transfer_act, wagon.number, transfer_act_wagon.with_property
                from transfer_act_wagon
                join wagon on wagon.id = transfer_act_wagon.wagon
                where transfer_act_wagon.transfer_act = any(:acts)
                order by transfer_act_wagon.transfer_act, transfer_act_wagon.seq
                """;
    }

    /**
     * Следующий номер акта.
     */
    static String nextActId() {
        return "select coalesce(max(transfer_act.id), 0) + 1 as next_id from transfer_act";
    }

}
