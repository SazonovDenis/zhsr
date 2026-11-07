package kz.zhsr.app.dto.request;

import java.util.Map;

/**
 * Сколько заявок по фильтру в каждом статусе и каждой критичности - числа у кнопок фильтров.
 *
 * @param requestStatusType id статуса (request_status_type) - число заявок
 * @param severityType      id критичности (severity_type) - число заявок
 */
public record RequestCounts(Map<Long, Integer> requestStatusType, Map<Long, Integer> severityType) {
}
