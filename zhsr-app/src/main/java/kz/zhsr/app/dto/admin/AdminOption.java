package kz.zhsr.app.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Вариант выбора в форме администрирования: id записи таблицы-справочника и её текст.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminOption {
    private long id;
    private String text;
}
