package kz.zhsr.app.dto.route;

import java.util.List;

/**
 * Фильтр списка маршрутов.
 *
 * @param depot  id депо: маршрут идёт через станцию этого депо
 * @param kinds  вид маршрута (константы RouteKind)
 * @param states состояния маршрута: active, closed
 */
public record RouteFilter(Long depot, List<Integer> kinds, List<String> states) {
}
