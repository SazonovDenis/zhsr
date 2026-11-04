package kz.zhsr.app.dto;

/**
 * Сотрудник для назначения начальником поезда или ПЭМ.
 *
 * @param usr  usr.id
 * @param name фамилия и инициалы
 */
public record StaffCandidate(long usr, String name) {
}
