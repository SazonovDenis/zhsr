package kz.zhsr.app.dto.user;

import java.util.Map;

/**
 * Сводка для сотрудника.
 *
 * @param counters счётчики пунктов левого меню: id пункта из menu.json - число; пусто - счётчиков нет
 */
public record CurrentState(Map<String, Integer> counters) {
    public CurrentState {
        counters = counters == null ? Map.of() : Map.copyOf(counters);
    }
}
