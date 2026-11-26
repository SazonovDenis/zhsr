package kz.zhsr.app.service;

import java.util.Map;

/**
 * Сообщения пользователю, когда запись не удалить, потому что на неё ссылается внешний ключ: по имени ключа fk_xxx.
 * Ключа нет в списке - {@link #DEFAULT}.
 */
public final class FkMessages {

    private FkMessages() {
    }

    public static final String DEFAULT = "На запись есть ссылки";

    private static final Map<String, String> MESSAGES = Map.ofEntries(
            // маршрут
            Map.entry("fk_trip_route", "Маршрут нельзя удалить: по нему есть рейсы"),
            Map.entry("fk_train_wagon_change_route_station",
                    "Остановку маршрута нельзя удалить: на ней есть прицепки или отцепки"),
            // поезд
            Map.entry("fk_trip_train", "Поезд нельзя удалить: у него есть рейсы"),
            Map.entry("fk_train_wagon_train", "Поезд нельзя удалить: в нём есть или были вагоны"),
            Map.entry("fk_train_wagon_change_train", "Поезд нельзя удалить: у него есть прицепки и отцепки"),
            Map.entry("fk_train_wagon_seq_train", "Поезд нельзя удалить: у него есть смены порядка вагонов"),
            Map.entry("fk_train_wagon_seq_route_station",
                    "Остановку маршрута нельзя удалить: на ней есть смены порядка вагонов"),
            // рейс
            Map.entry("fk_train_wagon_seq_trip", "Рейс нельзя удалить: в нём есть смены порядка вагонов"),
            Map.entry("fk_check_list_trip", "Рейс нельзя удалить: по нему есть чек-листы"),
            Map.entry("fk_equip_list_trip", "Рейс нельзя удалить: по нему есть описи"),
            Map.entry("fk_repair_request_trip", "Рейс нельзя удалить: по нему есть заявки"),
            Map.entry("fk_train_wagon_change_trip", "Рейс нельзя удалить: в нём есть прицепки или отцепки"),
            Map.entry("fk_wagon_info_trip", "Рейс нельзя удалить: в нём сейчас вагоны"),
            Map.entry("fk_wagon_location_trip", "Рейс нельзя удалить: вагоны были в этом рейсе"),
            Map.entry("fk_wagon_run_trip", "Рейс нельзя удалить: по нему учтён пробег вагонов"),
            // вагон
            Map.entry("fk_check_list_wagon", "Вагон нельзя удалить: по нему есть чек-листы"),
            Map.entry("fk_equip_list_wagon", "Вагон нельзя удалить: по нему есть описи"),
            Map.entry("fk_repair_request_wagon", "Вагон нельзя удалить: по нему есть заявки"),
            Map.entry("fk_train_wagon_wagon", "Вагон нельзя удалить: он есть или был в составе поезда"),
            Map.entry("fk_train_wagon_change_wagon", "Вагон нельзя удалить: его прицепляли или планировали прицепить"),
            Map.entry("fk_train_wagon_seq_item_wagon",
                    "Вагон нельзя удалить: он есть в сменах порядка вагонов поезда"),
            Map.entry("fk_transfer_act_wagon_wagon", "Вагон нельзя удалить: по нему есть акты передачи"),
            Map.entry("fk_wagon_edit_wagon", "Вагон нельзя удалить: у него есть журнал правок"),
            Map.entry("fk_wagon_location_wagon", "Вагон нельзя удалить: у него есть журнал расположения"),
            Map.entry("fk_wagon_responsible_wagon", "Вагон нельзя удалить: у него есть журнал ответственных"),
            Map.entry("fk_wagon_status_wagon", "Вагон нельзя удалить: у него есть журнал состояний"),
            Map.entry("fk_wagon_run_wagon", "Вагон нельзя удалить: по нему учтён пробег"),
            // журналы вагона - удаляются при удалении вагона
            Map.entry("fk_train_wagon_change_wagon_location",
                    "Вагон нельзя удалить: его прицепляли или отцепляли"),
            Map.entry("fk_train_wagon_change_wagon_responsible",
                    "Вагон нельзя удалить: его прицепляли или отцепляли"),
            Map.entry("fk_train_wagon_change_closed_wagon_responsible",
                    "Вагон нельзя удалить: его прицепляли или отцепляли"),
            Map.entry("fk_train_wagon_change_wagon_status",
                    "Вагон нельзя удалить: его прицепляли или отцепляли"));

    /** Сообщение по имени ключа; ключ неизвестен или пуст - {@link #DEFAULT}. */
    public static String of(String constraint) {
        return constraint == null ? DEFAULT : MESSAGES.getOrDefault(constraint, DEFAULT);
    }
}
