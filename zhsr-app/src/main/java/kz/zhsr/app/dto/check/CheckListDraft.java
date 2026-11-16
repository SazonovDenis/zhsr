package kz.zhsr.app.dto.check;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Завершённый чек-лист (Ф-ЧЕК-03); фото пунктов - части запроса photo_&lt;id пункта шаблона&gt;. При исправлении
 * чек-листа шаблон и объект проверки берутся из сохранённого чек-листа.
 *
 * @param checkListTemplate шаблон чек-листа
 * @param wagon             вагон - при проверке вагона
 * @param trip              рейс - при проверке сотрудника и рейса; у проверки вагона рейс определяет Система
 * @param checkedUsr        проверяемый сотрудник - при проверке сотрудника
 * @param createdDt         когда проверка начата; null или позже времени сервера - время сервера
 * @param values            значения пунктов
 */
public record CheckListDraft(
        long checkListTemplate,
        Long wagon,
        Long trip,
        Long checkedUsr,
        OffsetDateTime createdDt,
        List<CheckValueDraft> values
) {
}
