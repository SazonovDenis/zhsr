package kz.zhsr.app.dto.check;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Фильтр списка проверок (Ф-ЧЕК-04): шаблон, вагон, поиск по номеру вагона, период начала проверки.
 */
public record CheckListFilter(
        Long template,
        Long wagon,
        String query,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
) {
}
