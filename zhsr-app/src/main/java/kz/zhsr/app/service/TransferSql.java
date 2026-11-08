package kz.zhsr.app.service;

/**
 * SQL актов приёма-передачи ({@link TransferService}).
 */
final class TransferSql {

    private TransferSql() {
    }

    /**
     * Вагон - как строка таблицы вагонов (ListSql), и акт, который ждёт приёма по нему: номер и передаётся ли вагон в
     * нём с имуществом. Параметр pending - статус акта «ожидает приёма».
     */
    private static final String WAGONS = """
            select wagon.id, wagon.number, wagon.wagon_type, wagon.model_type, wagon.depot,
                   wagon_info.location_type,
                   coalesce(wagon_info.station, wagon_info.depot, wagon_info.yard, wagon_info.trip) as place,
                   wagon_info.trip, wagon_info.dbeg_location, wagon_info.wagon_status_type,
                   wagon_info.dbeg_status,
                   (select transfer_act.number
                    from transfer_act_wagon
                    join transfer_act on transfer_act.id = transfer_act_wagon.transfer_act
                    where transfer_act_wagon.wagon = wagon.id and transfer_act.act_status_type = :pending
                    limit 1) as pending_act,
                   (select transfer_act_wagon.with_property
                    from transfer_act_wagon
                    join transfer_act on transfer_act.id = transfer_act_wagon.transfer_act
                    where transfer_act_wagon.wagon = wagon.id and transfer_act.act_status_type = :pending
                    limit 1) as pending_with_property
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
     * Сколько вагонов стоит в каждом расположении станции: на самой станции, в её депо и парках отстоя. Расположений
     * без вагонов нет. Параметр: station.
     */
    static String placeWagons() {
        return """
                select wagon_info.station, wagon_info.depot, wagon_info.yard, count(*) as wagon_count
                from wagon_info
                left join depot on depot.id = wagon_info.depot
                left join yard on yard.id = wagon_info.yard
                where coalesce(wagon_info.station, depot.station, yard.station) = :station
                group by wagon_info.station, wagon_info.depot, wagon_info.yard
                """;
    }

    /**
     * Сотрудники с ролью, чьё текущее депо - при станции. Параметры: role, station.
     */
    static String stationStaff() {
        return """
                select usr.id
                from usr
                join usr_role on usr_role.usr = usr.id
                join usr_depot on usr_depot.usr = usr.id and usr_depot.dend is null
                join depot on depot.id = usr_depot.depot
                where usr_role.role_type = :role and not usr.disabled and depot.station = :station
                order by usr.full_name, usr.id
                """;
    }

    /**
     * Акты, новые первыми. Станция акта - станция расположения «откуда». Условия - по заданным параметрам.
     *
     * @param station отбор по параметру station - станция акта
     * @param states  отбор по параметру states
     * @param one     отбор по параметру id
     */
    static String acts(boolean station, boolean states, boolean one) {
        return """
                select transfer_act.id, transfer_act.number,
                       transfer_act.from_station, transfer_act.from_depot, transfer_act.from_yard,
                       transfer_act.to_station, transfer_act.to_depot, transfer_act.to_yard, transfer_act.work_type,
                       transfer_act.act_status_type, transfer_act.transferred_dt, transfer_act.received_dt,
                       transfer_act.reject_reason, transfer_act.transferred_by_usr, transfer_act.received_by_usr,
                       coalesce(transfer_act.from_station, depot.station, yard.station) as station
                from transfer_act
                left join depot on depot.id = transfer_act.from_depot
                left join yard on yard.id = transfer_act.from_yard
                where true
                """
                + (station ? " and coalesce(transfer_act.from_station, depot.station, yard.station) = :station" : "")
                + (states ? " and transfer_act.act_status_type = any(:states)" : "")
                + (one ? " and transfer_act.id = :id" : "")
                + " order by transfer_act.id desc limit 500";
    }

    /**
     * Сколько актов ждёт приёма сотрудником: на станцию принимает приёмщик, в депо или парк отстоя - мастер депо, на
     * станции своего депо; администратор - все. Станция акта - станция расположения «откуда». Параметры: pending -
     * статус «ожидает приёма»; admin; receiver, master - роли сотрудника; station - станция его депо.
     */
    static String pendingForMe() {
        return """
                select count(*) as pending_count
                from transfer_act
                left join depot on depot.id = transfer_act.from_depot
                left join yard on yard.id = transfer_act.from_yard
                where transfer_act.act_status_type = :pending
                  and (:admin
                       or (coalesce(transfer_act.from_station, depot.station, yard.station) = :station
                           and ((transfer_act.to_station is not null and :receiver)
                                or (transfer_act.to_station is null and :master))))
                """;
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
