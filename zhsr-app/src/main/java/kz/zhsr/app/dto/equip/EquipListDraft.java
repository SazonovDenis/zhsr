package kz.zhsr.app.dto.equip;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Завершённая опись экипировки (Ф-ЭКП-02). При исправлении описи шаблон и вагон берутся из сохранённой описи.
 *
 * @param equipListTemplate шаблон описи
 * @param wagon             вагон
 * @param createdDt         когда опись начата; null или позже времени сервера - время сервера
 * @param values            позиции: фактическое количество, примечание, фото
 */
public record EquipListDraft(
        long equipListTemplate,
        long wagon,
        OffsetDateTime createdDt,
        List<EquipValueDraft> values
) {
}
