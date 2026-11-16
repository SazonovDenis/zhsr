package kz.zhsr.app.dto.equip;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Фильтр списка описей (Ф-ЭКП-03): шаблон, вагон, поиск по номеру вагона, период начала описи.
 */
public record EquipListFilter(
        Long template,
        Long wagon,
        String query,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
) {
}
