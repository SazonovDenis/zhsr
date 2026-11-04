package kz.zhsr.app.dto;

/**
 * Новая или исправленная заявка (Ф-ЗАЯ-01, Ф-ЗАЯ-07); фото - отдельными частями запроса.
 *
 * @param wagon        вагон (wagon)
 * @param trip         рейс; null - без рейса
 * @param faultType    неисправность (fault_type)
 * @param severityType критичность (severity_type); null - из неисправности
 * @param description  описание
 * @param executorUsr  исполнитель; задаёт только мастер депо при подаче, null - не назначать
 */
public record RequestDraft(
        Long wagon,
        Long trip,
        Long faultType,
        Long severityType,
        String description,
        Long executorUsr
) {
}
